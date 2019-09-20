package org.broadinstitute.monster.ingester.jade.models

import java.time.OffsetDateTime

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Case class to represent the response received from the Jade API upon an ingest request or when checking
 * the status of a job.
 *
 * @param completed The datetime of when the job is completed (will only exist once the job is completed).
 * @param id The ID of the job.
 * @param status The status of the job, which may be running, succeeded, or failed.
 * @param submitted The datetime of when the job is submitted (will only exist once the job is submitted).
 */
case class JobInfo(completed: Option[OffsetDateTime], id: String, status: JobStatus, submitted: Option[OffsetDateTime])

object JobInfo {
  implicit val decoder: Decoder[JobInfo] = deriveDecoder(io.circe.derivation.renaming.snakeCase)
  implicit val encoder: Encoder[JobInfo] = deriveEncoder(io.circe.derivation.renaming.snakeCase)
}
