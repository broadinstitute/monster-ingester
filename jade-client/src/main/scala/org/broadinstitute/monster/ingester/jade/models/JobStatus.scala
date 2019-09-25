package org.broadinstitute.monster.ingester.jade.models

import enumeratum.EnumEntry.Lowercase
import enumeratum.{CirceEnum, Enum, EnumEntry}

import scala.collection.immutable.IndexedSeq

/** Possible job status responses from the Jade Data Repo API for ingest requests. */
sealed trait JobStatus extends EnumEntry with Product with Serializable with Lowercase

object JobStatus extends Enum[JobStatus] with CirceEnum[JobStatus] {

  override val values: IndexedSeq[JobStatus] = findValues

  /** Status for ingest jobs that are running. */
  case object Running extends JobStatus

  /** Status for ingest jobs that have completed successfully. */
  case object Succeeded extends JobStatus

  /** Status for ingest jobs that have failed. */
  case object Failed extends JobStatus
}
