package org.broadinstitute.monster.ingester

import cats.effect.IO
import fs2.Stream
import io.circe.Json
import org.http4s.headers._

/** Client which can perform I/O operations against Google Cloud Storage. */
trait JadeApi {

  /**
  *
   * @param datasetId
   * @param data
   * @return
   */
  def ingest(datasetId: String, data: IngestRequest): IO[JobStatus]

  /**
  *
   * @param jobId
   * @return
   */
  def jobStatus(jobId: String): IO[JobStatus]

  def apiStatus: IO[JadeStatus]
}
