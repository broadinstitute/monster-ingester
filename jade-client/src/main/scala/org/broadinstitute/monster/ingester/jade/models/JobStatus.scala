package org.broadinstitute.monster.ingester.jade.models

import doobie.postgres.{Instances => PostgresInstances}
import doobie.util.Meta
import enumeratum.EnumEntry.Lowercase
import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.circe.{KeyDecoder, KeyEncoder}

import scala.collection.immutable.IndexedSeq

/** Possible job status responses from the Jade Data Repo API for ingest requests. */
sealed trait JobStatus extends EnumEntry with Product with Serializable with Lowercase

object JobStatus
    extends Enum[JobStatus]
    with CirceEnum[JobStatus]
    with PostgresInstances {

  override val toString: String = "TransferStatus"

  override val values: IndexedSeq[JobStatus] = findValues

  implicit val statusMeta: Meta[JobStatus] =
    pgEnumStringOpt("job_status", namesToValuesMap.get, _.entryName)

  implicit val statusEncoder: KeyEncoder[JobStatus] =
    KeyEncoder.encodeKeyString.contramap(_.entryName)
  implicit val statusDecoder: KeyDecoder[JobStatus] =
    namesToValuesMap.get

  /** Status for ingest jobs that are running. */
  case object Running extends JobStatus

  /** Status for ingest jobs that have completed successfully. */
  case object Succeeded extends JobStatus

  /** Status for ingest jobs that have failed. */
  case object Failed extends JobStatus

  /** Status for ingest jobs that have not yet been submitted. */
  case object Pending extends JobStatus
}
