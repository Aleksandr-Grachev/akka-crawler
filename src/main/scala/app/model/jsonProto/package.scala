package app.model

import spray.json._
import spray.json.DefaultJsonProtocol._
import org.apache.pekko.http.scaladsl.model.Uri
import _root_.scala.util.control.NonFatal

package object jsonProto extends DefaultJsonProtocol {

  implicit val uriFormat: RootJsonFormat[Uri] =
    new RootJsonFormat[Uri] {
      def read(json: JsValue): Uri =
        json match {

          case JsString(value) =>
            try {
              val ret = Uri.parseAbsolute(org.parboiled2.ParserInput(value))
              ret
            } catch {
              case NonFatal(ex) =>
                deserializationError(
                  s"The given value[$value] isn't a valid URI"
                )
            }

          case other =>
            deserializationError("An JsString expected with Uri format")

        }
      def write(obj: Uri): JsValue = JsString(obj.toString())
    }
  implicit val urlRequestFormat: RootJsonFormat[UrlRequest] = jsonFormat1(
    UrlRequest
  )

  implicit val titleAnswerOkFormat: RootJsonFormat[Ok] = jsonFormat1(Ok.apply)
  implicit val titleAnswerErrFormat: RootJsonFormat[Err] = jsonFormat2(
    Err.apply
  )

  implicit val titleAnswerFormat: RootJsonFormat[TitleAnswer] =
    new RootJsonFormat[TitleAnswer] {
      def read(json: JsValue): TitleAnswer = {
        if (json.asJsObject.fields.contains("title")) {
          json.convertTo[Ok]
        } else {
          json.convertTo[Err]
        }
      }
      def write(obj: TitleAnswer): JsValue =
        obj match {
          case err: Err => err.toJson
          case ok:  Ok  => ok.toJson
        }
    }

  implicit val titleFormat: RootJsonFormat[Title] = jsonFormat2(Title)
}
