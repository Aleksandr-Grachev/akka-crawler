package app

import org.apache.pekko.Done
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.util.ByteString
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import spray.json._

import java.io.IOException
import java.net.URI
import scala.concurrent._
import scala.io.StdIn
import scala.util._
import scala.util.matching.Regex

object Main {

  val HTTP_PORT  = 7777
  val STASH_SIZE = 100

  def main(args: Array[String]): Unit = {

    import app.model._
    import app.model.jsonProto._

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
