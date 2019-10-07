package org.broadinstitute.monster.ingester.core.models

import java.time.OffsetDateTime

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}
import org.broadinstitute.monster.ingester.jade.models.JobStatus

case class RequestSummary(
  submitted: OffsetDateTime,
  statusCounts: List[(Long, JobStatus)]
)

object RequestSummary {
  implicit val decoder: Decoder[RequestSummary] = deriveDecoder
  implicit val encoder: Encoder[RequestSummary] = deriveEncoder
}
