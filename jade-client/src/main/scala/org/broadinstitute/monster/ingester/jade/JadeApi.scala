package org.broadinstitute.monster.ingester.jade

import cats.effect.IO
import org.broadinstitute.monster.ingester.jade.models.{
  IngestRequest,
  JadeStatus,
  JobInfo
}

/** Client which can interact with Jade's Data Repository API.
  * @see https://datarepo.terra.bio/swagger-ui.html#/ for Swagger and documentation of API.
  */
trait JadeApi {

  /**
    * Method to hit the Jade Data Repository's ingest endpoint.
    *
    * @param datasetId The ID of the dataset in the Jade Data Repository.
    * @param data Data body including the path to the data to be ingested and the table to ingest into.
    * @return JobInfo which captures important aspects of the API's response like status, job id, submission time,
    *         and completion time.
    *
    * @see https://datarepo.terra.bio/swagger-ui.html#/repository/ingestDataset for endpoint documentation.
    */
  def ingest(datasetId: String, data: IngestRequest): IO[JobInfo]

  /**
    * Method to hit the Jade Data Repository's job status endpoint.
    *
    * @param jobId The ID of the job to check the status of.
    * @return JobInfo which captures important aspects of the API's response like status, job id, submission time,
    *         and completion time.
    *
    * @see https://datarepo.terra.bio/swagger-ui.html#/repository/retrieveJob for endpoint documentation.
    */
  def jobStatus(jobId: String): IO[JobInfo]

  /**
    * Simple status check of the Jade API.
    *
    * @return Whether or not the Jade API is ok and the associated http status code.
    *
    * @see https://datarepo.terra.bio/swagger-ui.html#/unauthenticated/serviceStatus for endpoint documentation.
    */
  def apiStatus: IO[JadeStatus]
}
