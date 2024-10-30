package app.model

import spray.json._
import spray.json.DefaultJsonProtocol._
import org.apache.pekko.http.scaladsl.model.Uri
import _root_.scala.util.control.NonFatal

package object jsonProto {

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
}
