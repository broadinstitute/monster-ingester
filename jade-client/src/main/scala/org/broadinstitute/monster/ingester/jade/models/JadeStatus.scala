package org.broadinstitute.monster.ingester.jade.models

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
  * Reported status of Jade API.
  *
  * @param ok indication of whether or not the Jade API is up.
  * @param status http status code from the API
  *
  */
case class JadeStatus(ok: Boolean, status: Int)

object JadeStatus {
  implicit val decoder: Decoder[JadeStatus] = deriveDecoder
  implicit val encoder: Encoder[JadeStatus] = deriveEncoder
}
