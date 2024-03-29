package org.broadinstitute.monster.ingester.core.models

import java.util.UUID

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

sealed trait ApiError extends Exception with Product with Serializable

object ApiError {

  /**
    * Exception used to mark when a user attempts to interact
    * with a nonexistent request.
    */
  case class NotFound(requestId: UUID) extends ApiError

  implicit val nsrDecoder: Decoder[NotFound] = deriveDecoder
  implicit val nsrEncoder: Encoder[NotFound] = deriveEncoder
}
