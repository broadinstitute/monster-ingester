package org.broadinstitute.monster.ingester.jade

import java.nio.file.Path

import cats.effect.{Clock, ContextShift, IO, Resource}
import io.circe.jawn.JawnParser
import org.broadinstitute.monster.ingester.jade.models.ApiError.JadeError
import org.broadinstitute.monster.ingester.jade.models.{
  ApiErrorBody,
  IngestRequest,
  JadeStatus,
  JobInfo
}
import org.broadinstitute.monster.storage.gcs.GcsAuthProvider
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client._

/**
  * Client which can interact with Jade's Data Repository API.
  *
  * @param runHttp function which can transform HTTP requests into HTTP responses
  *                (bracketed by connection-management code).
  *
  * @see https://datarepo.terra.bio/swagger-ui.html#/ for Swagger and documentation of API.
  */
private class JadeApiClient(runHttp: Request[IO] => Resource[IO, Response[IO]])
    extends JadeApi {
  import JadeApiClient._

  override def ingest(datasetId: String, data: IngestRequest): IO[JobInfo] = {
    runHttp(
      Request[IO](method = Method.POST, JadeApiIngestEndpoint / s"$datasetId" / "ingest")
        .withEntity(data)
    ).use { response =>
      if (response.status.isSuccess) {
        response.as[JobInfo]
      } else {
        handleResponseError(response)
      }
    }
  }

  override def jobStatus(jobId: String): IO[JobInfo] = {
    runHttp(Request[IO](method = Method.GET, uri = JadeApiJobStatusEndpoint / jobId)).use {
      response =>
        if (response.status.isSuccess) {
          response.as[JobInfo]
        } else {
          handleResponseError(response)
        }
    }
  }

  override def apiStatus: IO[JadeStatus] = {
    runHttp(Request[IO](method = Method.GET, uri = JadeApiStatusEndpoint)).use {
      response =>
        IO.pure(JadeStatus(ok = response.status.isSuccess, response.status.code))
    }
  }

  /** parser used to decode bytes in error handling method. */
  private val parser = new JawnParser

  /**
    * Method to handle Jade API error responses.
    *
    * @param response The http4s response from the API
    */
  private def handleResponseError(response: Response[IO]): IO[Nothing] = {
    response.body.compile.toChunk.flatMap { chunk =>
      val parsed = parser.decodeByteBuffer[ApiErrorBody](chunk.toByteBuffer)
      IO.raiseError(JadeError(response.status.code, parsed.left.map { _ =>
        new String(chunk.toArray[Byte])
      }))
    }
  }
}

object JadeApiClient {

  private[this] val JadeBaseUri: Uri = uri"https://datarepo.terra.bio"

  private[jade] val JadeApiStatusEndpoint = JadeBaseUri / "status"

  private val JadeApiRepoBaseUri = JadeBaseUri / "api" / "repository" / "v1"

  private[jade] val JadeApiJobStatusEndpoint = JadeApiRepoBaseUri / "jobs"

  private[jade] val JadeApiIngestEndpoint = JadeApiRepoBaseUri / "datasets"

  def build(
    httpClient: Client[IO],
    serviceAccountJson: Option[Path]
  )(implicit cs: ContextShift[IO], clk: Clock[IO]): IO[JadeApi] =
    GcsAuthProvider.build(serviceAccountJson).map { auth =>
      /*
       * NOTE: Injecting the call to `.addAuth` here instead of in the class body
       * has the following trade-off:
       *
       *   1. It prevents the auth-adding logic from being covered by unit tests, BUT
       *   2. It makes it impossible for the business logic in the class body to
       *      "forget" to add auth, so as long as we get the logic correct here we
       *      can be confident that HTTP calls in the client won't fail from that
       *      particular bug
       *
       * 2. is a huge win for code cleanliness and long-term maintenance. Our integration
       * tests will thoroughly cover this builder method, so 1. isn't that big of a problem.
       */
      new JadeApiClient(
        req => Resource.liftF(auth.addAuth(req)).flatMap(httpClient.run)
      )
    }
}
