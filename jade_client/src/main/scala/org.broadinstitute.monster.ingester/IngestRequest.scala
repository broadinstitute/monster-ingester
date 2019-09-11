package org.broadinstitute.monster.ingester

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

case class IngestRequest (path: String, table: String)

object IngestRequest {
  implicit val decoder: Decoder[IngestRequest] = deriveDecoder
  implicit val encoder: Encoder[IngestRequest] = deriveEncoder // ADD THIS TO FLESH OUT THE REST OF THE REQUEST .mapJsonObject()
}
