package org.broadinstitute.monster.ingester.jade.models

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.literal._
import io.circe.{Decoder, Encoder}

/**
 * Represents the important user inputs for an ingest request.
 *
 * @param path The path of the data to ingest.
 * @param table The table to ingest data in to.
 */
case class IngestRequest (path: String, table: String)

object IngestRequest {
  implicit val decoder: Decoder[IngestRequest] = deriveDecoder
  implicit val encoder: Encoder[IngestRequest] = deriveEncoder.mapJsonObject( currentJson => currentJson
    .add("format", json"""json""").add("max_bad_records", json"""0"""))
}
