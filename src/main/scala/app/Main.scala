package app

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.typed.scaladsl.ActorFlow
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import spray.json._

import java.util.concurrent.Executors
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

object Main {
  import app.actor.Worker
  // TODO: move as params to app conf
  val HTTP_PORT                = 7777
  val STASH_SIZE               = 100
  val POLL_SIZE                = 10
  val THREAD_POOL_SIZE         = Runtime.getRuntime().availableProcessors()
  val ASK_TIMEOUT              = 60.seconds
  val FOLLOW_LOCATION          = true
  val FOLLOW_LOCATION_MAX_DEPT = 3

  def main(args: Array[String]): Unit = {

    import app.model._
    import app.model.jsonProto._

    implicit val timeout: Timeout = Timeout(ASK_TIMEOUT)

    val fixedEC: ExecutionContext = ExecutionContext.fromExecutorService(
      Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    )

    val pool: PoolRouter[Worker.Command] =
      Routers
        .pool(poolSize = POLL_SIZE) {
          Behaviors
            .supervise(
              Worker(
                bufferSize = STASH_SIZE,
                followLocation = FOLLOW_LOCATION,
                followMaxDepth = FOLLOW_LOCATION_MAX_DEPT
              )(fixedEC)
            )
            .onFailure[Exception](SupervisorStrategy.restart)
        }
        .withRoundRobinRouting()

    implicit val rootBehaviour: ActorSystem[Worker.Command] = ActorSystem(
      pool,
      "crawler-system"
    )

    implicit val executionContext = rootBehaviour.executionContext

    val workerFlow: Flow[Uri, String, NotUsed] =
      ActorFlow
        .ask(POLL_SIZE)(rootBehaviour)(makeMessage =
          (uri: Uri, replyTo: ActorRef[Worker.Event]) =>
            Worker.Command.DoRequest(uri = uri, replyTo = replyTo)
        )
        .map {
          case Worker.Event.Err(uri, status, message) =>
            Title(
              uri = uri,
              answer = Err(status = status, message = message)
            ).toJson.prettyPrint

          case Worker.Event.Ok(uri, value) =>
            Title(
              uri = uri,
              answer = Ok(title = value)
            ).toJson.prettyPrint
        }

    val route =
      path("urls") {
        post {
          entity(as[UrlRequest]) { urlRequest: UrlRequest =>
            val source =
              Source(urlRequest.urls)
                .via(workerFlow)
                .map(ByteString.apply)

            complete {
              HttpEntity(
                ContentTypes.`application/json`,
                source
              )
            }
          }
        }
      }

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
