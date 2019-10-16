package org.broadinstitute.monster.ingester.core

import java.time.Instant

import cats.implicits._
import doobie.util.fragment.Fragment
import doobie.implicits._

object Constants {

  val RequestsTable = "requests"
  val JobsTable = "jobs"

  val JoinTable: Fragment = List(
    Fragment.const(JobsTable),
    fr"t JOIN",
    Fragment.const(RequestsTable),
    fr"r ON t.request_id = r.id"
  ).combineAll

  def timestampSql(now: Instant): String =
    s"TO_TIMESTAMP(${now.toEpochMilli}::double precision / 1000)"
}
