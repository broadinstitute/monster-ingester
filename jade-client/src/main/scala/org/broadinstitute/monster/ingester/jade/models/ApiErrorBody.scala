package org.broadinstitute.monster.ingester.jade.models

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
  * Case class to represent a response from the Jade API when an API-side error occurs (this is just the body, not
  * the status code).
  *
  * @param errorDetail A list of error details from the Jade API.
  * @param message A specific error message for the particular type of error.
  */
case class ApiErrorBody(errorDetail: Option[List[String]], message: String)

object ApiErrorBody {
  implicit val decoder: Decoder[ApiErrorBody] = deriveDecoder
  implicit val encoder: Encoder[ApiErrorBody] = deriveEncoder
}
