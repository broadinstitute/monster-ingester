package org.broadinstitute.monster.ingester.core

import java.time.{Instant, OffsetDateTime}
import java.util.UUID

import cats.effect.{Async, Clock, IO}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.log.LogHandler
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.monster.ingester.core.ApiError.NotFound
import org.broadinstitute.monster.ingester.core.IngestController.JobSubmission
import org.broadinstitute.monster.ingester.core.models.{
  IngestData,
  JobData,
  JobSummary,
  RequestSummary
}
import org.broadinstitute.monster.ingester.jade.JadeApiClient
import org.broadinstitute.monster.ingester.jade.models.{IngestRequest, JobStatus}

/**
  * Component responsible for backing Ingester's API and providing the core logic for ingesting.
  *
  * This component has methods to process requests and add them to the database, expand them
  * out into jobs, submit those jobs to the Jade API, and update the status of jobs. This component
  * also provides methods by which a user can check on the status of a request or the status of all
  * jobs under a request.
  *
  * @param dbClient Client to interact with Ingester's backing database.
  * @param jadeClient Client to interact with the Jade Data Repository API.
  */
class IngestController(dbClient: Transactor[IO], jadeClient: JadeApiClient)(
  implicit clk: Clock[IO]
) {

  import DoobieInstances._
  import Constants._

  private val logger = Slf4jLogger.getLogger[IO]
  private implicit val logHandler: LogHandler = DbLogHandler(logger)

  /**
    * Main ingest method to run all methods to create a request entry and subsequent job entries.
    *
    * @param ingestData the list of paths and table names to ingest from/to.
    * @param datasetId the dataset ID to ingest into.
    * @return the UUID of the request created.
    */
  def ingest(ingestData: IngestData, datasetId: UUID): IO[UUID] = {
    for {
      now <- getNow
      rId <- createRequest(datasetId, now)
      _ <- initJobs(ingestData, rId)
    } yield {
      rId
    }
  }

  def getNow: IO[Instant] =
    clk.realTime(scala.concurrent.duration.MILLISECONDS).map(Instant.ofEpochMilli)

  /**
    * Update the requests table with a request.
    *
    * @param datasetId Id of the dataset in the Repo to ingest data into.
    * @param now the current time for timestamps.
    * @return the Id of the newly added request.
    */
  def createRequest(datasetId: UUID, now: Instant): IO[UUID] = {
    // create request id
    val requestId = UUID.randomUUID()

    // TODO we might want to consider hitting Jade's API to validate the datasetId before
    // inserting it in the requests table
    val updateSql = List(
      Fragment.const(s"INSERT INTO $RequestsTable (id, submitted, dataset_id) VALUES"),
      fr"($requestId," ++ Fragment.const(timestampSql(now)) ++ fr", $datasetId)"
    ).combineAll

    // return id and add to requestTable
    updateSql.update.withUniqueGeneratedKeys[UUID]("id").transact(dbClient)
  }

  /**
    * Update the jobs table with one job for every path/table combination in a request.
    *
    * @param ingestData the list of paths and table names used to create jobs.
    * @param requestId the Id of the request under which to create jobs.
    * @return the number of jobs added to the jobs table.
    */
  def initJobs(ingestData: IngestData, requestId: UUID): IO[Int] = {
    checkAndExec(requestId) { rId =>
      // generate UUID for each subrequest in a request
      val jobs = ingestData.tables.map {
        case JobData(prefix, tableName) =>
          (
            rId,
            JobStatus.Pending: JobStatus,
            prefix,
            tableName // TODO maybe include updated timestamp in the initialization?
          )
      }
      // update table
      Update[(UUID, JobStatus, String, String)](
        s"INSERT INTO $JobsTable (request_id, status, path, table_name) VALUES (?, ?, ?, ?)"
      ).updateMany(jobs)
    }
  }

  // TODO maybe break out all these steps into individual private methods
  // need a method (this should maybe be in another class, like the listener in Transporter) to submit jobs to Jade
  def submitJobs(maxJobsAllowed: Int): IO[Int] = {
    val transaction = for {
      // see how many jobs are running
      runningCount <- List(
        Fragment.const(s"SELECT COUNT(*) FROM $JobsTable"),
        fr"WHERE status = ${JobStatus.Running: JobStatus}"
      ).combineAll
        .query[Long]
        .unique

      // select the dataset IDs, table names, and path names to submit
      jobsToSubmit <- List(
        Fragment.const(
          s"SELECT t.id, r.id, t.path, t.table_name, r.dataset_id FROM"
        ),
        JoinTable,
        fr"WHERE t.status = ${JobStatus.Pending: JobStatus}",
        fr"LIMIT ${maxJobsAllowed - runningCount}"
      ).combineAll
        .query[JobSubmission]
        .to[List]

      // submit (max number of jobs - number of jobs running) to Jade API
      statuses <- Async[ConnectionIO].liftIO(
        jobsToSubmit.traverse { job =>
          jadeClient.ingest(job.datasetId, IngestRequest(job.path, job.tableName)).map {
            ingested =>
              (
                job.id,
                ingested.id,
                ingested.jobStatus,
                ingested.completed,
                ingested.submitted
              )
          }
        }
      )

      // update the JobsTable; update the requests that have moved from pending to running
      numUpdated <- statuses.traverse { row =>
        val completedFragment =
          handleOptionalTimestamp(row._4, "completed", includeCommaBefore = true)
        val submittedFragment = handleOptionalTimestamp(
          row._5,
          "submitted",
          includeCommaBefore = true,
          includeCommaAfter = true
        )
        val updatedFragment = handleOptionalTimestamp(row._5, "updated")
        List(
          Fragment.const(s"UPDATE $JobsTable"),
          fr"SET jade_id = ${row._2},",
          fr"status = ${row._3}",
          completedFragment,
          submittedFragment,
          updatedFragment,
          fr"WHERE id = ${row._1}"
        ).combineAll.update.run
      }
    } yield {
      numUpdated.sum
    }

    transaction.transact(dbClient)
  }

  /**
    * Provides the submission time and count of all jobs grouped by status.
    *
    * @param requestId Id of the request to check the status of.
    * @return a RequestSummary for the given requestId.
    */
  def requestStatus(requestId: UUID): IO[RequestSummary] = {
    checkAndExec(requestId) { rId =>
      for {
        submittedTime <- List(
          Fragment.const(s"SELECT submitted FROM $RequestsTable"),
          fr"WHERE id = $rId LIMIT 1"
        ).combineAll
          .query[OffsetDateTime]
          .unique
        counts <- List(
          Fragment.const(s"SELECT COUNT(*), status FROM $JobsTable"),
          fr"WHERE request_id = $rId",
          fr"GROUP BY status"
        ).combineAll
          .query[(Long, JobStatus)]
          .to[List]
      } yield {
        RequestSummary(submittedTime, counts)
      }
    }
  }

  /**
    * Provides the Jade Id, status, path of files being ingested, table name, submitted time, and completed time for
    * all jobs under a request.
    *
    * @param requestId Id of the request for which to enumerate jobs.
    * @return a list of job summaries for all jobs under the request.
    */
  def enumerateJobs(requestId: UUID): IO[List[JobSummary]] = {
    checkAndExec(requestId) { rId =>
      List(
        Fragment.const(
          s"SELECT jade_id, status, path, table_name, submitted, completed FROM $JobsTable"
        ),
        fr"WHERE request_id = $rId"
      ).combineAll
        .query[JobSummary]
        .to[List]
    }
  }

  // TODO need a method for our API's status, return a status type for the api (new type)
  def apiStatus: IO[ApiStatus] = {
    ???
  }

  // TODO consider breaking this up into multiple methods
  /**
    * Hits the Jade API to check for status updates on running jobs.
    *
    * @param limit maximum number of jobs to update in one go.
    * @param now current time for timestamps.
    * @return the number of jobs updated.
    */
  def updateJobStatus(limit: Int, now: Instant): IO[Int] = {
    // sweep jobs db for jobs that have ids
    val transaction = for {
      ids <- List(
        Fragment.const(s"SELECT jade_id FROM $JobsTable"),
        fr"WHERE jade_id IS NOT NULL",
        fr"AND status = ${JobStatus.Running: JobStatus}",
        fr"ORDER BY updated ASC LIMIT $limit"
      ).combineAll
        .query[UUID]
        .to[List]

      // hit jade job result endpoint for each job
      statuses <- Async[ConnectionIO].liftIO(ids.traverse { id =>
        jadeClient.jobStatus(id)
      })

      // if status has changed, update, else remain (upsert)
      numUpdated <- statuses.traverse { row =>
        val completedFragment =
          handleOptionalTimestamp(row.completed, "completed", includeCommaAfter = true)
        List(
          Fragment.const(s"UPDATE $JobsTable"),
          fr"SET status = ${row.jobStatus},",
          completedFragment,
          fr"updated = " ++ Fragment.const(timestampSql(now)),
          fr"WHERE jade_id = ${row.id}"
        ).combineAll.update.run
      }
    } yield {
      numUpdated.sum
    }
    transaction.transact(dbClient)
  }

  /**
    * Check that a request with the given ID exists, then run an operation using the ID as input.
    *
    * @param requestId Id of the request to check and use.
    *  @return the result of whatever function
    */
  private def checkAndExec[Out](requestId: UUID)(
    f: UUID => ConnectionIO[Out]
  ): IO[Out] = {
    val transaction = for {
      requestRow <- List(
        Fragment.const(s"SELECT 1 FROM $RequestsTable"),
        fr"WHERE id = $requestId LIMIT 1"
      ).combineAll
        .query[Long]
        .option
      _ <- IO
        .raiseError(NotFound(requestId))
        .whenA(requestRow.isEmpty)
        .to[ConnectionIO]
      out <- f(requestId)
    } yield {
      out
    }

    transaction.transact(dbClient)
  }

  /**
    * Create a Fragment correctly for a given timestamp and field name.
    *
    * @param ts the possible OffsetDateTime representation of a timestamp.
    * @param field the field name of the relevant timestamp column in the database.
    * @param includeCommaAfter if it is true then the fragment will end with a comma and a space.
    * @return a correctly formatted Fragment if Some, else an empty Fragment.
    */
  private def handleOptionalTimestamp(
    ts: Option[OffsetDateTime],
    field: String,
    includeCommaBefore: Boolean = false,
    includeCommaAfter: Boolean = false
  ): Fragment = {
    val beforeComma = if (includeCommaBefore) fr", " else fr""
    val afterComma = if (includeCommaAfter) fr", " else fr""
    ts match {
      case Some(ts) =>
        beforeComma ++ Fragment.const(s"$field = ") ++ Fragment.const(
          timestampSql(ts.toInstant)
        ) ++ afterComma
      case None => fr""
    }
  }
}

object IngestController {

  /**
    * Convenience case class to make submitJobs more readable.
    *
    * @param id the job Id that we generate.
    * @param requestId the request Id that we generate.
    * @param path the path to ingest from.
    * @param tableName the table name to ingest to.
    * @param datasetId the Id of the dataset in the Repo to ingest into.
    */
  private case class JobSubmission(
    id: Long,
    requestId: UUID,
    path: String,
    tableName: String,
    datasetId: UUID
  )
}
