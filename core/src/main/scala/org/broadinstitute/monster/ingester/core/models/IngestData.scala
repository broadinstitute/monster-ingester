package org.broadinstitute.monster.ingester.core.models

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

/**
 * Class that represents the data body associated with ingest requests to the Ingester.
 *
 * @param tables list of JobData to capture key information for ingest requests.
 */
case class IngestData(tables: List[JobData])

object IngestData {
  implicit val decoder: Decoder[IngestData] = deriveDecoder
  implicit val encoder: Encoder[IngestData] = deriveEncoder
}
