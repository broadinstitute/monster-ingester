package org.broadinstitute.monster.ingester.jade.models

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s._

sealed trait ApiError extends Exception with Product with Serializable

object ApiError {

  /**
   * Case class to represent the entire response from the Jade API when an API-side error occurs.
   *
   * @param status The http status code of the error.
   * @param body The http body associated with the error.
   */
  case class JadeError(status: Status, body: Either[String, ApiErrorBody]) extends ApiError

  implicit val brDecoder: Decoder[JadeError] = deriveDecoder
  implicit val brEncoder: Encoder[JadeError] = deriveEncoder
}
