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

object Main {

  val STASH_SIZE = 100

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "my-system")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext

    val route =
      path("hello") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              "<h1>Say hello to Pekko HTTP</h1>"
            )
          )
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(
      s"Server now online. Please navigate to http://localhost:8080/hello\nPress RETURN to stop..."
    )
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
