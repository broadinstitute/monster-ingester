package org.broadinstitute.monster.ingester.core

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Instant, OffsetDateTime}
import java.util.UUID

import cats.effect.{Clock, IO, Resource}
import cats.implicits._
import doobie.implicits._
import doobie.Transactor
import doobie.util.fragment.Fragment
import fs2.Stream
import org.broadinstitute.monster.ingester.core.ApiError.NotFound
import org.broadinstitute.monster.ingester.core.models.{IngestData, JobData}
import org.broadinstitute.monster.ingester.jade.JadeApiClient
import org.broadinstitute.monster.ingester.jade.models.JobStatus
import org.http4s.{Request, Response, Status}
import org.scalamock.scalatest.MockFactory
import org.scalatest.EitherValues

import scala.concurrent.duration.TimeUnit

class IngestControllerSpec extends PostgresSpec with MockFactory with EitherValues {

  import DoobieInstances._
  import Constants._

  val nowMillis = 1234L
  private implicit val clk: Clock[IO] = new Clock[IO] {
    override def realTime(unit: TimeUnit): IO[Long] = IO.pure(nowMillis)
    override def monotonic(unit: TimeUnit): IO[Long] = IO.pure(nowMillis)
  }

  private val dataset1Id = UUID.randomUUID()

  private val request1Id = UUID.randomUUID()
  private val request1Jobs = List.tabulate(10) { i =>
    s"path$i" -> s"table$i"
  }

  private val request2Id = UUID.randomUUID()
  private val request2Jobs = List.tabulate(20) { i =>
    s"path$i" -> s"table$i"
  }

  private val request3Id = UUID.randomUUID()

  private def buildApi(run: Request[IO] => Resource[IO, Response[IO]]): JadeApiClient =
    new JadeApiClient(run)

  def withController(
    jadeClient: JadeApiClient
  )(test: (Transactor[IO], IngestController) => IO[Any]): Unit = {
    val tx = transactor
    test(tx, new IngestController(tx, jadeClient)).unsafeRunSync()
    ()
  }

  def withRequest(
    jadeClient: JadeApiClient
  )(test: (Transactor[IO], IngestController) => IO[Any]): Unit =
    withController(jadeClient) { (tx, controller) =>
      val setup = for {
        _ <- List(request1Id, request2Id, request3Id).zipWithIndex.traverse_ {
          case (id, i) =>
            val ts = Timestamp.from(Instant.ofEpochMilli(nowMillis + i))
            sql"INSERT INTO requests (id, submitted, dataset_id) VALUES ($id, $ts, $dataset1Id)".update.run.void
        }
        _ <- request1Jobs.traverse_ {
          case (path, table) =>
            sql"""INSERT INTO jobs
                  (request_id, status, path, table_name)
                  VALUES
                  ($request1Id, ${JobStatus.Pending: JobStatus}, $path, $table)""".update.run.void
        }
        _ <- request2Jobs.traverse_ {
          case (path, table) =>
            sql"""INSERT INTO jobs
                  (request_id, status, path, table_name)
                  VALUES
                  ($request2Id, ${JobStatus.Pending: JobStatus}, $path, $table)""".update.run.void
        }
      } yield ()

      setup.transact(tx).flatMap(_ => test(tx, controller))
    }

  private val apiEmpty = buildApi { _ =>
    Resource.pure(Response[IO]())
  }

  private val apiIngest = buildApi { _ =>
    val thetimestring =
      OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    Resource.liftF(
      IO.pure(
        Response[IO](
          status = Status.Ok,
          body = Stream.emits(
            s"""{ "status_code": 200, "id": "${UUID
              .randomUUID()}", "job_status": "running", "submitted": "$thetimestring" }""".getBytes
          )
        )
      )
    )
  }

  def getNow: IO[Instant] =
    clk.realTime(scala.concurrent.duration.MILLISECONDS).map(Instant.ofEpochMilli)

  behavior of "IngestController"

  // createRequest
  it should "return a UUID for valid inputs" in withController(apiEmpty) {
    (_, controller) =>
      for {
        now <- getNow
        a <- controller.createRequest(dataset1Id, now)
      } yield {
        a
      }
  }

  it should "insert correct data into the requests table" in withController(apiEmpty) {
    (tx, controller) =>
      for {
        now <- getNow
        rId <- controller.createRequest(dataset1Id, now)
        count <- List(
          Fragment.const(s"SELECT COUNT(*) FROM $RequestsTable"),
          fr"WHERE id = $rId"
        ).combineAll
          .query[Long]
          .unique
          .transact(tx)
        real <- List(
          Fragment.const(s"SELECT id, submitted, dataset_id FROM $RequestsTable"),
          fr"WHERE id = $rId"
        ).combineAll
          .query[(UUID, OffsetDateTime, UUID)]
          .unique
          .transact(tx)
      } yield {
        count shouldBe 1
        real._1 shouldBe rId
        real._2.toInstant shouldBe now
        real._3 shouldBe dataset1Id
      }
  }

  // initJobs
  it should "return the number of jobs added" in withRequest(apiEmpty) {
    (_, controller) =>
      val myData1 = IngestData(
        List(
          JobData("mypath1", "mytable1"),
          JobData("mypath2", "mytable2")
        )
      )
      val myData2 = IngestData(
        List(
          JobData("mypath3", "mytable3"),
          JobData("mypath4", "mytable4"),
          JobData("mypath4", "mytable4")
        )
      )
      for {
        count1 <- controller.initJobs(myData1, request1Id)
        count2 <- controller.initJobs(myData2, request2Id)
      } yield {
        count1 shouldBe 2
        count2 shouldBe 3
      }
  }

  it should "initialize data correctly into the jobs table" in withRequest(apiEmpty) {
    (tx, controller) =>
      val myData = IngestData(
        List(
          JobData("mypath1", "mytable1"),
          JobData("mypath2", "mytable2")
        )
      )
      for {
        _ <- controller.initJobs(myData, request3Id)
        real <- List(
          Fragment.const(
            s"SELECT id, request_id, status, path, table_name FROM $JobsTable"
          ),
          fr"WHERE request_id = $request3Id"
        ).combineAll
          .query[(Long, UUID, JobStatus, String, String)]
          .to[List]
          .transact(tx)
      } yield {
        real should contain theSameElementsAs myData.tables.zipWithIndex.map {
          case (JobData(path, tableName), i) =>
            // have to add the number of jobs that already exist, and then +1 because SQL starts at 1, NOT 0 (boo)
            (
              i + 1 + request1Jobs.length + request2Jobs.length,
              request3Id,
              JobStatus.Pending,
              path,
              tableName
            )
        }
      }
  }

  it should "raise a NotFound error if job initialization is attempted under a nonexistent request" in withController(
    apiEmpty
  ) { (_, controller) =>
    val myData = IngestData(
      List(
        JobData("mypath1", "mytable1"),
        JobData("mypath2", "mytable2")
      )
    )
    // note that this fails as desired because we are using WithController, so request1Id is not in the DB
    controller
      .initJobs(myData, request1Id)
      .attempt
      .map(_.left.value shouldBe NotFound(request1Id))
  }

  // submitJobs
  it should "return the number of jobs that have been updated to 'running'" in withRequest(
    apiIngest
  ) { (tx, controller) =>
    for {
      now <- getNow
      count <- controller.submitJobs(5, now)
      real <- List(
        Fragment.const(s"SELECT COUNT(*) FROM jobs"),
        fr"WHERE status = ${JobStatus.Running: JobStatus}"
      ).combineAll
        .query[Long]
        .unique
        .transact(tx)
    } yield {
      count shouldBe real
    }
  }

  // requestStatus
  it should "return correctly formatted request status" in withRequest(apiEmpty) {
    (tx, controller) =>
      for {
        _ <- sql"""INSERT INTO jobs
                  (request_id, status, path, table_name)
                  VALUES
                  ($request1Id, ${JobStatus.Running: JobStatus}, 'prunning', 'trunning')""".update.run.void
          .transact(tx)
        _ <- sql"""INSERT INTO jobs
                  (request_id, status, path, table_name)
                  VALUES
                  ($request1Id, ${JobStatus.Succeeded: JobStatus}, 'psucceeded', 'tsucceeded')""".update.run.void
          .transact(tx)
        _ <- sql"""INSERT INTO jobs
                  (request_id, status, path, table_name)
                  VALUES
                  ($request1Id, ${JobStatus.Failed: JobStatus}, 'pfailed', 'tfailed')""".update.run.void
          .transact(tx)
        real <- controller.requestStatus(request1Id)
      } yield {
        real.statusCounts should contain theSameElementsAs List(
          (10, JobStatus.Pending),
          (1, JobStatus.Running),
          (1, JobStatus.Succeeded),
          (1, JobStatus.Failed)
        )
      }
  }

  it should "raise a NotFound error if the status of a nonexistent request is requested" in withController(
    apiEmpty
  ) { (_, controller) =>
    controller
      .requestStatus(request1Id)
      .attempt
      .map(_.left.value shouldBe NotFound(request1Id))
  }

  // enumerateJobs

  // updateJobStatus
}
