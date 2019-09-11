package org.broadinstitute.monster.ingester

import java.time.OffsetDateTime

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

// MAKE JOBSTATUS AN ENUM
case class JobStatus (completed: Option[OffsetDateTime], id: String, jobStatus: String, submitted: OffsetDateTime)

object JobStatus {
  implicit val decoder: Decoder[JobStatus] = deriveDecoder(io.circe.derivation.renaming.snakeCase)
  implicit val encoder: Encoder[JobStatus] = deriveEncoder(io.circe.derivation.renaming.snakeCase)
}
