package org.broadinstitute.monster.ingester.core

import java.time.{Instant, OffsetDateTime}
import java.util.UUID

import cats.effect.{Async, Clock, IO}
import cats.implicits._
import doobie.{Fragment, _}
import doobie.util.transactor.Transactor
import doobie.implicits._
import org.broadinstitute.monster.ingester.jade.JadeApiClient
import org.broadinstitute.monster.ingester.jade.models.{JobInfo, JobStatus}

/** TODO DOCSTRING */
class IngestController(dbClient: Transactor[IO], jadeClient: JadeApiClient)(implicit clk: Clock[IO]) {

  import Constants._

  // need a method to run all steps of an ingest; returns the request ID to check the status of?
  def ingest(ingestRequest: IngestRequest, datasetId: UUID): IO[UUID] = ???

  private def getNow: IO[Instant] =
    clk.realTime(scala.concurrent.duration.MILLISECONDS).map(Instant.ofEpochMilli)

  /** TODO DOCSTRING */
  private def createRequest(datasetId: UUID): ConnectionIO[UUID] = {
    // create request id
    val requestId = UUID.randomUUID()
    val now = getNow.unsafeRunSync()

    val updateSql = List(
      Fragment.const(s"INSERT INTO $RequestsTable (id, submitted_at, dataset_id) VALUES"),
      fr"($requestId," ++ Fragment.const(timestampSql(now)) ++ fr", $datasetId)"
    ).combineAll

    // return id and add to requestTable
    for {
      requestId <- updateSql.update.withUniqueGeneratedKeys[UUID]("id")
    } yield {
      requestId
    }
  }

  // need a method to generate UUIDs for subRequests for a request and add them to the subRequestsTable, returns list of subreq ids
  private def createJobs(ingestRequest: IngestRequest, requestId: UUID): ConnectionIO[Unit] = {
    // generate UUID for each subrequest in a request
    val now = getNow.unsafeRunSync()
    val jobs = ingestRequest.tables.map { case JobData(prefix, tableName) =>
      (
        requestId,
        "pending", // TODO create enum class for this one's statuses
        prefix,
        tableName,
        timestampSql(now)
      )
    }
    // update table
    for {
      _ <- Update[(UUID, String, String, String, String)](
        s"INSERT INTO $JobsTable (request_id, status, path_prefix, table_name, submitted) VALUES (?, ?, ?, ?, ?)"
      ).updateMany(jobs)
    } yield ()
  }

  // need a method (this should maybe be in another class, like the listener in Transporter) to submit jobs to Jade
  private def submitJobs: ConnectionIO[List[UUID]] = {
    // see how many jobs are running

    // submit (max number of jobs - number of jobs running) to Jade API

    // update the JobsTable; update the requests that have moved from pending to running
  }

  /** TODO DOCSTRING */
  def requestStatus(requestId: UUID): ConnectionIO[RequestStatus] = {
    for {
      submittedTime <- List(
        Fragment.const(s"SELECT submitted FROM $RequestsTable"),
        fr"WHERE id = $requestId LIMIT 1"
      ).combineAll
        .query[OffsetDateTime]
        .unique
      counts <- List(
        Fragment.const(s"SELECT COUNT(*), status FROM $JobsTable"),
        fr"WHERE id = $requestId",
        fr"GROUP BY status"
      ).combineAll
        .query[(Long, String)]
        .to[List]
    } yield {
      RequestStatus(submittedTime, counts)
    }
  }

  /** TODO DOCSTRING */
  def enumerateJobs(requestId: UUID): ConnectionIO[List[JobSummary]] = {
    for {
      statuses <- List(
        Fragment.const(s"SELECT id, status, path, table, submitted, completed FROM $JobsTable"),
        fr"WHERE requestId = $requestId"
      ).combineAll
        .query[JobSummary]
        .to[List]
    } yield (statuses)
  }

  // need a method for our API's status, return a status type for the api (new type)
  def apiStatus: IO[ApiStatus] = {
    ???
  }

  /** TODO DOCSTRING */
  private def updateJobStatus(limit: Int, now: Instant): ConnectionIO[Int] = {
    // sweep jobs db for jobs that have ids
    for {
      ids <- List(
        Fragment.const(s"SELECT id FROM $JobsTable"),
        Fragments.whereAnd(
          fr"id IS NOT NULL",
          fr"completed IS NULL",
        ),
        fr"ORDER BY updated ASC LIMIT $limit"
      ).combineAll
        .query[UUID]
        .to[List]

      // hit jade job result endpoint for each job
      statuses <- Async[ConnectionIO].liftIO(ids.traverse { id => jadeClient.jobStatus(id) })

      // if status has changed, update, else remain (upsert)
      numUpdated <- Update[JobInfo](List(
        Fragment.const(s"UPDATE $JobsTable"),
        fr"SET status = t.status, completed = t.completed, submitted = t.submitted, updated =" ++ Fragment.const(timestampSql(now)),
        fr"FROM (VALUES (?, ?, ?, ?)) AS t (id, status, completed, submitted)",
        fr"WHERE id = t.id"
      ).combineAll.toString).updateMany(statuses)
    } yield {
      numUpdated
    }

  }

  // TODO make a helper that is like "validate request" or something like we have in transporter; not sure if needed

  /**
   * Check that a request with the given ID exists, then run an operation using the ID as input.
   *
   * TODO fill this out properly
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
