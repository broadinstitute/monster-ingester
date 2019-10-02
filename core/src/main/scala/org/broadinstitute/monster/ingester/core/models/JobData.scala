package org.broadinstitute.monster.ingester.core.models

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

/**
 * Class that represents each job request within an ingest request body made to the Ingester.
 *
 * @param path the path to the files to ingest (file or "directory" pattern).
 * @param tableName the name of the table in the Repo to ingest into.
 */
case class JobData(path: String, tableName: String)

object JobData {
  implicit val decoder: Decoder[JobData] = deriveDecoder
  implicit val encoder: Encoder[JobData] = deriveEncoder
}
