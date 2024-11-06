package app.actor

import app.actor.Worker.Command.DoFollowLocation
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.util.ByteString

import java.io.IOException
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util._
import scala.util.matching.Regex

import Worker._

class Worker(
    ctx:            ActorContext[Command],
    stashBuffer:    StashBuffer[Command],
    followLocation: Boolean,
    followMaxHop: Int
)(implicit
    ec: ExecutionContext
) {

  implicit val system: ActorSystem[Nothing] = ctx.system

  def ready(): Behavior[Command] =
    Behaviors.receiveMessagePartial[Command] {

      case Command.DoRequest(uri, replyTo) =>
        pipeDoRequest(uri = uri, replyTo = replyTo, depth = 0)
        busy()

    }

  def busy(): Behavior[Command] =
    Behaviors.receiveMessage[Command] {

      case cmd: Command.DoRequest =>
        stashBuffer.stash(cmd)
        Behaviors.same[Command]

      case DoFollowLocation(uri, replyTo, depth) =>
        pipeDoRequest(uri = uri, replyTo = replyTo, depth = depth)
        Behaviors.same[Command]

      case Command.DoAnswer(ev, replyTo) =>
        replyTo ! ev
        stashBuffer.unstashAll(ready())
    }

  def pipeDoRequest(
      uri:     Uri,
      replyTo: ActorRef[Worker.Event],
      depth:   Int
  ): Unit =
    ctx.pipeToSelf(doRequest(uri, replyTo, depth)) {
      case Failure(exception) =>
        Command.DoAnswer(
          Event.Err.general(
            uri,
            message = s"An error occurred[${exception.getMessage()}]"
          ),
          replyTo
        )
      case Success(value) => value
    }

  def doRequest(
      uri:          Uri,
      replyTo:      ActorRef[Event],
      currentDepth: Int
  ): Future[Command] =
    Http().singleRequest(HttpRequest(uri = uri)).flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        findTitle(entity = entity).map { title =>
          Command.DoAnswer(
            Event.Ok(
              uri = uri,
              title = title
            ),
            replyTo
          )

        }

      case response @ HttpResponse(status, headers, _, _)
          if followLocation &&
            status.isRedirection() &&
            currentDepth < followMaxHop =>
        val command: Command =
          headers
            .find(_.is("location")) match {

            case None =>
              Command.DoAnswer(
                Event.Err(
                  uri = uri,
                  status = Some(status.intValue()),
                  message =
                    "The http request failed because the redirect response did not contain a Location header"
                ),
                replyTo
              )

            case Some(locationHeader) =>
              val newUri = Uri.parseAndResolve(locationHeader.value(), uri)

              Command.DoFollowLocation(
                uri = newUri,
                replyTo = replyTo,
                depth = currentDepth + 1
              )

          }

        response.discardEntityBytes()

        Future.successful(command)

      case response @ HttpResponse(status, _, _, _)
          if followLocation && status.isRedirection() =>
        response.discardEntityBytes()
        Future.successful {
          Command.DoAnswer(
            Event.Err(
              uri = uri,
              status = Some(status.intValue()),
              message =
                "The http request failed because the maximum number of location hops has been reached"
            ),
            replyTo
          )
        }

      case response @ HttpResponse(status, _, _, _) =>
        response.discardEntityBytes()
        Future.successful {
          Command.DoAnswer(
            Event.Err(
              uri = uri,
              status = Some(status.intValue()),
              message = "The http request was unsuccessful"
            ),
            replyTo
          )
        }

    }

  def findTitle(entity: ResponseEntity): Future[String] =
    entity.dataBytes.runFold(ByteString(""))(_ ++ _).map { byteString =>
      val utf8String = byteString.utf8String
      titlePattern.findFirstMatchIn(utf8String).map(_.group(1)) match {
        case None =>
          throw new IOException(
            s"Received reponse doesn't contain title tag[${utf8String.take(100)}...]"
          )

        case Some(title) => title

      }
    }

}

object Worker {

  val titlePattern: Regex = """(?i)<title>(.*?)</title>""".r

  sealed trait Command

  object Command {
    final case class DoRequest(uri: Uri, replyTo: ActorRef[Event])
        extends Command

    private[Worker] final case class DoFollowLocation(
        uri:     Uri,
        replyTo: ActorRef[Event],
        depth:   Int
    ) extends Command

    object DoFollowLocation {
      def startFollowLocation(
          uri:     Uri,
          replyTo: ActorRef[Event]
      ): DoFollowLocation =
        new DoFollowLocation(uri, replyTo = replyTo, depth = 0)
    }

    private[Worker] final case class DoAnswer(
        ev:      Event,
        replyTo: ActorRef[Event]
    ) extends Command
  }

  sealed trait Event

  object Event {

    final case class Err(uri: Uri, status: Option[Int], message: String)
        extends IOException(message)
        with Event

    object Err {
      def general(uri: Uri, message: String): Err =
        new Err(uri = uri, status = None, message = message)
    }

    final case class Ok(uri: Uri, title: String) extends Event
  }

  def apply(bufferSize: Int, followLocation: Boolean, followMaxHop: Int)(
      implicit ec: ExecutionContext
  ) =
    Behaviors.setup[Command] { ctx: ActorContext[Command] =>
      Behaviors.withStash[Command](bufferSize) { stashBuffer =>
        new Worker(
          ctx = ctx,
          stashBuffer = stashBuffer,
          followLocation = followLocation,
          followMaxHop = followMaxHop
        ).ready()
      }

    }
}
