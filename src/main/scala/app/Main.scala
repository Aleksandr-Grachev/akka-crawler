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
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.http.scaladsl.model.HttpEntity.Chunk
import app.actor.Master
import java.util.concurrent.Executors
import org.apache.pekko.util.Timeout
import scala.concurrent.duration._

object Main {

  //TODO: move as params to app conf
  val HTTP_PORT        = 7777
  val STASH_SIZE       = 100
  val POLL_SIZE        = 10
  val THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors()
  val ASK_TIMEOUT      = 60.seconds

  def main(args: Array[String]): Unit = {

    import app.model._
    import app.model.jsonProto._
    import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable

    implicit val timeout: Timeout = Timeout(ASK_TIMEOUT)

    val fixedEC: ExecutionContext = ExecutionContext.fromExecutorService(
      Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    )

    implicit val rootBehaviour: ActorSystem[Master.Command] = ActorSystem(
      Master(
        poolSize = POLL_SIZE,
        bufferSize = STASH_SIZE
      )(fixedEC),
      "crawler-system"
    )

    implicit val scheduler: Scheduler = rootBehaviour.scheduler

    implicit val executionContext = rootBehaviour.executionContext

    val numbers =
      Source.fromIterator(() => Iterator.continually(Random.nextInt(100)))

    val route =
      path("urls") {
        post {
          entity(as[UrlRequest]) { urlRequest: UrlRequest =>
            complete {
              rootBehaviour
                .ask[Master.Event] { replyTo =>
                  Master.Command.DoRequest(request = urlRequest, replyTo)
                }
                .map { case Master.Event.EventTitles(titles) =>
                  titles
                }
            }
          }
        }
      } // ~
    // path("random") {
    //   get {
    //     complete(
    //       HttpEntity.Chunked(
    //         ContentTypes.`text/plain(UTF-8)`,
    //         // transform each number to a chunk of bytes
    //         numbers.map {
    //           case n if n < 77 =>
    //             HttpEntity.Chunk(s"$n\n")

    //           case x: Int =>
    //             HttpEntity.LastChunk(
    //               s"Last $x" //ext
    //             ) 
    //         }
    //       )
    //     )
    //   }
    // }

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

    CoordinatedShutdown(rootBehaviour).addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      "info"
    ) { () =>
      println("Shuting down crawler")
      Future.successful(Done)
    }

  }

}
