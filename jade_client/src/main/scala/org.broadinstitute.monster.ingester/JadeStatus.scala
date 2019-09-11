package org.broadinstitute.monster.ingester

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

/**
 * Reported status of Jade Api.
 *
 * @param ok indication of whether or not the Jade Api is up
 * @param message more information describing the current state of the system
 *
 */
case class JadeStatus(ok: Boolean, message: String)

object JadeStatus {
  implicit val decoder: Decoder[JadeStatus] = deriveDecoder
  implicit val encoder: Encoder[JadeStatus] = deriveEncoder
}
