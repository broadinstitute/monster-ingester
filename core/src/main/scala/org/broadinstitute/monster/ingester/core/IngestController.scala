package org.broadinstitute.monster.ingester.core

import java.time.{Instant, OffsetDateTime}
import java.util.UUID

import cats.effect.{Async, Clock, IO}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.broadinstitute.monster.ingester.core.ApiError.NotFound
import org.broadinstitute.monster.ingester.core.IngestController.JobSubmission
import org.broadinstitute.monster.ingester.core.models.{
  IngestData,
  JobData,
  JobSummary,
  RequestSummary
}
import org.broadinstitute.monster.ingester.jade.JadeApiClient
import org.broadinstitute.monster.ingester.jade.models.{IngestRequest, JobInfo, JobStatus}

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

  // need a method to run all steps of an ingest; returns the request ID to check the status of?
  def ingest(ingestRequest: IngestData, datasetId: UUID): IO[UUID] = ???

  private def getNow: IO[Instant] =
    clk.realTime(scala.concurrent.duration.MILLISECONDS).map(Instant.ofEpochMilli)

  /**
    * Update the requests table with a request.
    *
    * @param datasetId Id of the dataset in the Repo to ingest data into.
    * @param now the current time for timestamps.
    * @return the Id of the newly added request.
    */
  private def createRequest(datasetId: UUID, now: Instant): ConnectionIO[UUID] = {
    // create request id
    val requestId = UUID.randomUUID()

    val updateSql = List(
      Fragment.const(s"INSERT INTO $RequestsTable (id, submitted_at, dataset_id) VALUES"),
      fr"($requestId, ${timestampSql(now)}, $datasetId)"
    ).combineAll

    // return id and add to requestTable
    for {
      requestId <- updateSql.update.withUniqueGeneratedKeys[UUID]("id")
    } yield {
      requestId
    }
  }

  /**
    * Update the jobs table with one job for every path/table combination in a request.
    *
    * @param ingestData the list of paths and table names used to create jobs.
    * @param requestId the Id of the request under which to create jobs.
    * @return the number of jobs added to the jobs table.
    */
  private def initJobs(ingestData: IngestData, requestId: UUID): IO[Int] = {
    checkAndExec(requestId) { rId =>
      // generate UUID for each subrequest in a request
      val jobs = ingestData.tables.map {
        case JobData(prefix, tableName) =>
          (
            rId,
            JobStatus.Pending: JobStatus,
            prefix,
            tableName
          )
      }
      // update table
      for {
        jobsAdded <- Update[(UUID, JobStatus, String, String)](
          s"INSERT INTO $JobsTable (request_id, status, path_prefix, table_name) VALUES (?, ?, ?, ?)"
        ).updateMany(jobs)
      } yield (jobsAdded)
    }
  }

  // TODO maybe break out all these steps into individual private methods
  // need a method (this should maybe be in another class, like the listener in Transporter) to submit jobs to Jade
  private def submitJobs(maxJobsAllowed: Int, now: Instant): ConnectionIO[Int] = {
    for {
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
          s"SELECT t.id, r.id, t.path, t.table, r.datasetId FROM $JoinTable"
        ),
        fr"WHERE status = ${JobStatus.Pending: JobStatus}",
        fr"LIMIT ${maxJobsAllowed - runningCount}"
      ).combineAll
        .query[JobSubmission]
        .to[List]

      // submit (max number of jobs - number of jobs running) to Jade API
      newIds <- Async[ConnectionIO].liftIO(
        jobsToSubmit.traverse { job =>
          jadeClient.ingest(job.datasetId, IngestRequest(job.path, job.table)).map {
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
      numUpdated <- Update[
        (Long, UUID, JobStatus, Option[OffsetDateTime], Option[OffsetDateTime])
      ](
        List(
          Fragment.const(s"UPDATE $JobsTable"),
          fr"SET status = t.status, jade_id = t.jade_id, submitted = t.submitted, updated =" ++ Fragment
            .const(timestampSql(now)),
          fr"FROM (VALUES (?, ?, ?, ?, ?)) AS t (id, jade_id, status, completed, submitted)",
          fr"WHERE id = t.id"
        ).combineAll.toString
      ).updateMany(newIds)
    } yield {
      numUpdated
    }
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
          fr"WHERE id = $rId",
          fr"GROUP BY status"
        ).combineAll
          .query[(Long, String)]
          .to[List]
      } yield {
        models.RequestSummary(submittedTime, counts)
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
      for {
        statuses <- List(
          Fragment.const(
            s"SELECT repo_id, status, path, table, submitted, completed FROM $JobsTable"
          ),
          fr"WHERE requestId = $rId"
        ).combineAll
          .query[JobSummary]
          .to[List]
      } yield (statuses)
    }
  }

  // need a method for our API's status, return a status type for the api (new type)
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
  private def updateJobStatus(limit: Int, now: Instant): ConnectionIO[Int] = {
    // sweep jobs db for jobs that have ids
    for {
      ids <- List(
        Fragment.const(s"SELECT id FROM $JobsTable"),
        Fragments.whereAnd(
          fr"id IS NOT NULL",
          fr"status = ${JobStatus.Running: JobStatus}"
        ),
        fr"ORDER BY updated ASC LIMIT $limit"
      ).combineAll
        .query[UUID]
        .to[List]

      // hit jade job result endpoint for each job
      statuses <- Async[ConnectionIO].liftIO(ids.traverse { id =>
        jadeClient.jobStatus(id)
      })

      // if status has changed, update, else remain (upsert)
      numUpdated <- Update[JobInfo](
        List(
          Fragment.const(s"UPDATE $JobsTable"),
          fr"SET status = t.status, completed = t.completed, submitted = t.submitted, updated =" ++ Fragment
            .const(timestampSql(now)),
          fr"FROM (VALUES (?, ?, ?, ?)) AS t (id, status, completed, submitted)",
          fr"WHERE id = t.id"
        ).combineAll.toString
      ).updateMany(statuses)
    } yield {
      numUpdated
    }
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
}

object IngestController {

  /**
    * Convenience case class to make submitJobs more readable.
    *
    * @param id the job Id that we generate.
    * @param requestId the request Id that we generate.
    * @param path the path to ingest from.
    * @param table the table name to ingest to.
    * @param datasetId the Id of the dataset in the Repo to ingest into.
    */
  private case class JobSubmission(
    id: Long,
    requestId: UUID,
    path: String,
    table: String,
    datasetId: UUID
  )
}
