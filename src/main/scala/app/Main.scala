package app

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import org.apache.pekko.http.scaladsl.client
import java.net.URI
import java.io.IOException
import org.apache.pekko.actor.typed.ActorRef
import scala.concurrent.Future
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.Uri
import scala.util.Failure
import org.apache.pekko.actor.typed.scaladsl.StashBuffer
import app.Worker._
import org.apache.pekko.actor.typed.scaladsl.StashBuffer
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.Behavior
import scala.util.Success
import org.apache.pekko.http.scaladsl.model.StatusCodes
import scala.concurrent.ExecutionContext
import org.apache.pekko.http.scaladsl.model.ResponseEntity
import org.apache.pekko.util.ByteString
import scala.util.matching.Regex

object Main extends App {

  val STASH_SIZE = 100

}

class Worker(ctx: ActorContext[Command], stashBuffer: StashBuffer[Command])(
    implicit ec: ExecutionContext
) {

  implicit val system = ctx.system

  def ready(): Behavior[Command] =
    Behaviors.receiveMessagePartial[Command] {

      case Command.DoRequest(uri, replyTo) =>
        ctx.pipeToSelf(doRequest(uri, replyTo)) {
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

        busy()

    }

  def busy(): Behavior[Command] =
    Behaviors.receiveMessage[Command] {

      case cmd: Command.DoRequest =>
        stashBuffer.stash(cmd)
        Behaviors.same[Command]

      case Command.DoAnswer(ev, replyTo) =>
        replyTo ! ev
        stashBuffer.unstashAll(ready())
    }

  def doRequest(uri: Uri, replyTo: ActorRef[Event]): Future[Command] =
    Http().singleRequest(HttpRequest(uri = uri)).flatMap {
      case response @ HttpResponse(StatusCodes.OK, _, entity, _) =>
        findTitle(entity = entity).map { title =>
          Command.DoAnswer(
            Event.Ok(
              uri = uri,
              title = title
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
      byteString.utf8String match {
        case Worker.titlePattern(title) =>
          title
        case other =>
          throw new IOException(
            s"Received reponse doesn't contain title tag[${other.take(100)}...]"
          )
      }
    }

}

object Worker {

  val titlePattern: Regex = "(?i)title>(.+)<.*".r

  sealed trait Command

  object Command {
    final case class DoRequest(uri: Uri, replyTo: ActorRef[Event])
        extends Command

    final case class DoAnswer(ev: Event, replyTo: ActorRef[Event])
        extends Command
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

  def apply(bufferSize: Int)(implicit ec: ExecutionContext) =
    Behaviors.setup[Command] { ctx: ActorContext[Command] =>
      Behaviors.withStash[Command](bufferSize) { stashBuffer =>
        new Worker(ctx = ctx, stashBuffer = stashBuffer).ready()
      }

    }
}
