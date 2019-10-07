package org.broadinstitute.monster.ingester.jade

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import cats.effect.{IO, Resource}
import fs2.Stream
import io.circe.Json
import org.broadinstitute.monster.ingester.jade.JadeApiClient.{
  JadeApiIngestEndpoint,
  JadeApiJobStatusEndpoint,
  JadeApiStatusEndpoint
}
import org.broadinstitute.monster.ingester.jade.models.ApiError.JadeError
import org.broadinstitute.monster.ingester.jade.models.JobStatus.Running
import org.broadinstitute.monster.ingester.jade.models.{
  ApiErrorBody,
  IngestRequest,
  JadeStatus,
  JobInfo
}
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.scalatest.{FlatSpec, Matchers}

class JadeApiClientSpec extends FlatSpec with Matchers {
  // So we likely want a simple mock of the Jade API or something like that. Gonna try to piggyback off the GCS stuff

  private val datasetId = UUID.randomUUID()
  private val jobId = UUID.randomUUID()
  private val datasetData = IngestRequest("path", "table")

  private def buildApi(run: Request[IO] => Resource[IO, Response[IO]]): JadeApiClient =
    new JadeApiClient(run)

  behavior of "JadeApiClient"

  // ingest
  it should "return a properly formatted response of JobInfo when an ingest request is successfully requested" in {
    val thetime = OffsetDateTime.now()
    val thetimestring = thetime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    val api = buildApi { req =>
      req.method shouldBe Method.POST

      req.uri shouldBe JadeApiIngestEndpoint / datasetId.toString / "ingest"

      val expectedBody = Json.obj(
        "path" -> Json.fromString("path"),
        "table" -> Json.fromString("table"),
        "format" -> Json.fromString("json"),
        "max_bad_records" -> Json.fromInt(0)
      )

      Resource.liftF(req.as[Json]).map { actualBody =>
        actualBody shouldBe expectedBody
      }

      Resource.liftF(
        IO.pure(
          Response[IO](
            status = Status.Ok,
            body = Stream.emits(
              s"""{ "status_code": 200, "id": "$jobId", "job_status": "running", "submitted": "$thetimestring", "completed": "$thetimestring" }""".getBytes
            )
          )
        )
      )
    }
    val actualOk = api.ingest(datasetId, datasetData)
    val expectedOk = JobInfo(jobId, Running, Some(thetime), Some(thetime))
    actualOk.unsafeRunSync() shouldBe expectedOk
  }

  it should "return a properly formatted JadeError when an ingest request results in an error without a response body" in {
    val api = buildApi { req =>
      req.method shouldBe Method.POST

      val expectedBody = Json.obj(
        "path" -> Json.fromString("path"),
        "table" -> Json.fromString("table"),
        "format" -> Json.fromString("json"),
        "max_bad_records" -> Json.fromInt(0)
      )

      Resource.liftF(req.as[Json]).map { actualBody =>
        actualBody shouldBe expectedBody
      }

      Resource.liftF(
        IO.pure(
          Response[IO](
            status = Status.NotFound,
            body = Stream.empty
          )
        )
      )
    }
    val actualNotFound =
      api.ingest(UUID.randomUUID(), datasetData).attempt.unsafeRunSync()
    val errorResponse = actualNotFound.fold(_.leftSideValue, _.leftSideValue)
    val expectedUnauthorized = JadeError(404, Left(""))
    errorResponse shouldBe expectedUnauthorized
  }

  it should "return a properly formatted JadeError when an ingest request results in an error with a response body" in {
    val api = buildApi { req =>
      req.method shouldBe Method.POST

      Resource.liftF(
        IO.pure(
          Response[IO](
            status = Status.BadRequest,
            body = Stream.emits(
              """{ "errorDetail": ["detail1", "detail2"], "message": "message1" }""".getBytes
            )
          )
        )
      )
    }
    val actualBadRequest =
      api.ingest(UUID.randomUUID(), IngestRequest("herp", "derp")).attempt.unsafeRunSync()
    val errorResponse = actualBadRequest.fold(blah => blah, gah => gah)
    val expectedBadRequest =
      JadeError(400, Right(ApiErrorBody(Option(List("detail1", "detail2")), "message1")))
    errorResponse shouldBe expectedBadRequest
  }

  // jobStatus
  it should "return a properly formatted response of JobInfo when a status request is successfully requested" in {
    val thetime = OffsetDateTime.now()
    val thetimestring = thetime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    val api = buildApi { req =>
      req.method shouldBe Method.GET

      req.uri shouldBe JadeApiJobStatusEndpoint / jobId.toString

      Resource.liftF(
        IO.pure(
          Response[IO](
            status = Status.Ok,
            body = Stream.emits(
              s"""{ "status_code": 200, "id": "$jobId", "job_status": "running", "submitted": "$thetimestring", "completed": "$thetimestring" }""".getBytes
            )
          )
        )
      )
    }
    val actualOk = api.jobStatus(jobId)
    val expectedOk = JobInfo(jobId, Running, Some(thetime), Some(thetime))
    actualOk.unsafeRunSync() shouldBe expectedOk
  }

  it should "return a properly formatted JadeError when a status request results in an error without a response body" in {
    val badId = UUID.randomUUID()
    val api = buildApi { req =>
      req.method shouldBe Method.GET

      req.uri shouldBe JadeApiJobStatusEndpoint / badId.toString

      Resource.liftF(
        IO.pure(
          Response[IO](
            status = Status.NotFound,
            body = Stream.empty
          )
        )
      )
    }
    val actualNotFound = api.jobStatus(badId).attempt.unsafeRunSync()
    val errorResponse = actualNotFound.fold(_.leftSideValue, _.leftSideValue)
    val expectedUnauthorized = JadeError(404, Left(""))
    errorResponse shouldBe expectedUnauthorized
  }

  it should "return a properly formatted JadeError when a status request results in an error with a response body" in {
    val badId = UUID.randomUUID()

    val api = buildApi { req =>
      req.method shouldBe Method.GET

      req.uri shouldBe JadeApiJobStatusEndpoint / badId.toString

      Resource.liftF(
        IO.pure(
          Response[IO](
            status = Status.BadRequest,
            body = Stream.emits(
              """{ "errorDetail": ["detail1", "detail2"], "message": "message1" }""".getBytes
            )
          )
        )
      )
    }
    val actualBadRequest =
      api.jobStatus(badId).attempt.unsafeRunSync()
    val errorResponse = actualBadRequest.fold(blah => blah, gah => gah)
    val expectedBadRequest =
      JadeError(400, Right(ApiErrorBody(Option(List("detail1", "detail2")), "message1")))
    errorResponse shouldBe expectedBadRequest
  }

  // apiStatus
  it should "get a JadeStatus of success and handle it correctly" in {
    val api = buildApi { req =>
      req.method shouldBe Method.GET
      req.uri shouldBe JadeApiStatusEndpoint

      Resource.pure(
        Response[IO](status = Status.Ok)
      )
    }

    val actual = api.apiStatus
    val expected = JadeStatus(ok = true, 200)

    actual.unsafeRunSync() shouldBe expected
  }

  it should "get a JadeStatus of not okay and handle it correctly" in {
    val api = buildApi { req =>
      req.method shouldBe Method.GET
      req.uri shouldBe JadeApiStatusEndpoint

      Resource.liftF(
        IO.pure(
          Response[IO](status = Status.NotFound)
        )
      )
    }

    val actual = api.apiStatus
    val expected = JadeStatus(ok = false, 404)

    actual.unsafeRunSync() shouldBe expected
  }
}
