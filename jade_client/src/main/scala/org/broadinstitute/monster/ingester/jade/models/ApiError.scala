package org.broadinstitute.monster.ingester.jade.models

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.circe.syntax._

sealed trait ApiError extends Exception with Product with Serializable

object ApiError {

  /**
    * Case class to represent the entire response from the Jade API when an API-side error occurs.
    *
    * @param status The http status code of the error.
    * @param body The http body associated with the error.
    */
  case class JadeError(status: Int, body: Either[String, ApiErrorBody])
      extends Exception {
    override def getMessage: String = {
      val theBody =
        body.fold(theString => theString, apiErrorBody => apiErrorBody.toString)
      s"Status: $status, body: $theBody"
    }
  }

  implicit val d: Decoder[Either[String, ApiErrorBody]] =
    Decoder[String].either(Decoder[ApiErrorBody])
  implicit val decoder: Decoder[JadeError] = deriveDecoder
  implicit val e: Encoder[Either[String, ApiErrorBody]] = either =>
    either.fold(_.asJson, _.asJson)
  implicit val encoder: Encoder[JadeError] = deriveEncoder
}
