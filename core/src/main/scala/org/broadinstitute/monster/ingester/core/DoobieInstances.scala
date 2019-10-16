package org.broadinstitute.monster.ingester.core

import java.sql.Timestamp
import java.time.{OffsetDateTime, ZoneId, ZoneOffset}

import cats.implicits._
import doobie.{Get, Put}
import doobie.postgres.circe.Instances.JsonInstances
import doobie.postgres.{Instances => PostgresInstances}
import io.circe.{Decoder, Json, KeyDecoder}

import scala.reflect.runtime.universe.TypeTag

/**
  * Container for orphan typeclass instances needed by Transporter to
  * interact with Postgres via doobie.
  */
object DoobieInstances extends PostgresInstances with JsonInstances {

  /**
    * Typeclass which can read `OffsetDateTime`s from Postgres.
    *
    * First reads values as a SQL timestamp, then converts the underlying
    * instant into a datetime in UTC.
    */
  implicit val odtGet: Get[OffsetDateTime] = Get[Timestamp].tmap { ts =>
    OffsetDateTime.ofInstant(ts.toInstant, ZoneId.of("UTC"))
  }

  /**
    * Typeclass which can write `OffsetDatetime`s to Postgres.
    *
    * Converts an OffsetDateTime to a Java Timestamp which Doobie knows how to handle.
    */
  implicit val odtPut: Put[OffsetDateTime] = Put[Timestamp].contramap { ts =>
    Timestamp.valueOf(ts.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime)
  }

  /**
    * Derivation helper which can produce a typeclass for reading any concrete
    * `Map[K, V]` type from Postgres, as long as we know how to decode JSON into
    * an instance of that Map type.
    */
  implicit def mapGet[K: KeyDecoder: TypeTag, V: Decoder: TypeTag]: Get[Map[K, V]] =
    Get[Json].temap(_.hcursor.as[Map[K, V]].leftMap(_.getMessage()))

}
