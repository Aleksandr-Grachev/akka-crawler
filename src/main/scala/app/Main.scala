package app

import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.util.ByteString

import java.io.IOException
import java.net.URI
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.StdIn
import scala.util._
import scala.util.matching.Regex

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.Done
import spray.json._

object Main {

  val HTTP_PORT  = 7777
  val STASH_SIZE = 100
  final case class UrlRequest(urls: List[Uri])

  implicit val uriFormat: RootJsonFormat[Uri] =
    new RootJsonFormat[Uri] {
      def read(json: JsValue): Uri =
        json match {

          case JsString(value) =>
            try {
              val ret = Uri.parseAbsolute(org.parboiled2.ParserInput(value))
              ret
            } catch {
              case IllegalUriException(ex) =>
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
    UrlRequest.apply
  )

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "crawler-system")

    implicit val executionContext = system.executionContext

    val route =
      path("urls") {
        post {
          entity(as[UrlRequest]) { urlRequest: UrlRequest =>
            complete(
              HttpEntity(
                ContentTypes.`text/html(UTF-8)`,
                s"<h1>Say hello to Pekko HTTP[$urlRequest]</h1>"
              )
            )
          }

        }
      }

    val bindingFuture =
      Http().newServerAt("localhost", HTTP_PORT).bind(route).andThen {
        case Success(value) =>
          println(
            s"Server now online. Please navigate to http://localhost:$HTTP_PORT/urls"
          )

        case Failure(ex) =>
          println(
            s"Something get wrong[${ex.getMessage()}]"
          )
      }

    CoordinatedShutdown(system).addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      "info"
    ) { () =>
      println("Shuting down crawler")
      Future.successful(Done)
    }

  }

}
