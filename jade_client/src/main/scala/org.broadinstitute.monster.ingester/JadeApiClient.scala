package org.broadinstitute.monster.ingester

import java.nio.file.Path

import cats.effect.{Clock, ContextShift, IO, Resource}
import org.broadinstitute.monster.storage.gcs.GcsAuthProvider
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client._

// So we need credentials for hitting the jade api (don't know what form that'll be, maybe it's own type class?
// I'm not sure if we need anything else?

private class JadeApiClient(runHttp: Request[IO] => Resource[IO, Response[IO]])
    extends JadeApi {
  import JadeApiClient._
  // methods to hit:
  // 1) POST ingest /api/repository/v1/datasets/{id}/ingest, needs dataset ID, returns job ID and other info
  // 2) GET job status /api/repository/v1/jobs/{id}, needs job ID, returns job status
  // 3) GET api status /status, returns 200 code if healthy o/w bad bad not good

  def ingest(datasetId: String, data: IngestRequest): IO[JobStatus] = {
    runHttp(Request[IO](
      method = Method.POST,
      uri = JadeApiIngestEndpoint / s"$datasetId" / "ingest").withEntity(data)).use { response =>
      ???
    }
  }

  def jobStatus(jobId: String): IO[JobStatus] = {
    runHttp(Request[IO](method = Method.GET, uri = JadeApiJobStatusEndpoint / jobId)).use { response =>
      if (response.status.isSuccess) {
        response.as[JobStatus]
      } else { // NOT BE IO.pure, but raise error based on response code; if no error message body, then raise real bad error
        IO.pure(???)
      }
    }
  }
  // TODO: add docstrings for all of these and also change the output types to be IO[caseclass] and make the case class
  def apiStatus: IO[JadeStatus] = {
    runHttp(Request[IO](method = Method.GET, uri = JadeApiStatusEndpoint)).use { response =>
      if (response.status.isSuccess) {
        IO.pure(JadeStatus(ok = true, s"awwwww yeahhhh"))
      } else {
        IO.pure(JadeStatus(ok = false, s"oh no response code of ${response.status.toString()}"))
      }
    }
  }
}

object JadeApiClient {

  /** Base URI for Jade API. */
  private[this] val JadeBaseUri: Uri =
    uri"https://datarepo.terra.bio"

  private val JadeApiStatusEndpoint = JadeBaseUri / "status"

  private val JadeApiRepoBaseUri = JadeBaseUri / "api" / "repository" / "v1"

  private val JadeApiJobStatusEndpoint = JadeApiRepoBaseUri / "jobs"

  private val JadeApiIngestEndpoint = JadeApiRepoBaseUri / "datasets"

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
