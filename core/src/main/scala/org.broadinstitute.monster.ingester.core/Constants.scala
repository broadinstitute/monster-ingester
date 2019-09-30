package org.broadinstitute.monster.ingester.core

import java.time.Instant

object Constants {

  val RequestsTable = "requests"
  val JobsTable = "jobs"

  def timestampSql(now: Instant): String =
    s"TO_TIMESTAMP(${now.toEpochMilli}::double precision / 1000)"
}
