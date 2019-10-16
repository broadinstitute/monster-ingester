package org.broadinstitute.monster.ingester.core.models

import java.time.OffsetDateTime

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}
import org.broadinstitute.monster.ingester.jade.models.JobStatus

/**
 * Case class to model the information returned by the request status method.
 *
 * @param submitted the timestamp of when the request was submitted.
 * @param statusCounts a list of tuples with (number of jobs, status of jobs)
 */
case class RequestSummary(
  submitted: OffsetDateTime,
  statusCounts: List[(Long, JobStatus)]
)

object RequestSummary {
  implicit val decoder: Decoder[RequestSummary] = deriveDecoder
  implicit val encoder: Encoder[RequestSummary] = deriveEncoder
}
