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
import org.slf4j.LoggerFactory
import pureconfig._
import pureconfig.generic.auto._
import spray.json._

import java.util.concurrent.Executors
import scala.concurrent._
import scala.util._
import pureconfig.error.ConfigReaderFailures
import pureconfig.error.CannotRead

object Main {
  import app.actor.Worker

  def main(args: Array[String]): Unit = {

    import app.model._
    import app.model.config._
    import app.cli._
    import app.model.jsonProto._

    val log = LoggerFactory.getLogger("Main")

    val config: AppConf =
      (for {
        raw <- ConfigSource.default.config()
        app <- ConfigSource.fromConfig(raw).at("app").load[AppConf]
        mergedWithCmd <- loadAppConfig(args, app).left.map { ex: Exception =>
          ConfigReaderFailures(
            new CannotRead {
              override val sourceName = "command line"
              override val sourceType: String            = "command line args"
              override val reason:     Option[Throwable] = Some(ex)
            }
          )
        }
      } yield mergedWithCmd) match {
        case Left(value) =>
          log.error("Start application failed because of config failures")
          value.prettyPrint()
          sys.exit(1)
        case Right(conf) =>
          conf
      }

    implicit val timeout: Timeout = Timeout(config.askTimeout)

    val fixedEC: ExecutionContext =
      ExecutionContext.fromExecutorService(
        Executors.newFixedThreadPool(
          config.threadPoolSize.getOrElse(
            Runtime.getRuntime().availableProcessors()
          )
        )
      )

    val pool: PoolRouter[Worker.Command] =
      Routers
        .pool(poolSize = config.workerPoolSize) {
          Behaviors
            .supervise(
              Worker(
                bufferSize = config.stashSize,
                followLocation = config.followLocation,
                followMaxHop = config.followLocationMaxHop
              )(fixedEC)
            )
            .onFailure[Exception](SupervisorStrategy.restart)
        }
        .withRoundRobinRouting() // add explicity

    implicit val rootBehaviour: ActorSystem[Worker.Command] = ActorSystem(
      pool,
      "crawler-system"
    )

    // rootBehaviour.logConfiguration()

    implicit val executionContext = rootBehaviour.executionContext

    val workerFlow: Flow[Uri, String, NotUsed] =
      ActorFlow
        .ask(config.workerPoolSize)(rootBehaviour)(makeMessage =
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

    Http().newServerAt("localhost", config.httpPort).bind(route).andThen {
      case Success(value) =>
        println(
          s"Server now online. Please navigate to http://localhost:${config.httpPort}/urls"
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
