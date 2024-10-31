package app.actor

import app.model._
import org.apache.pekko.actor.typed._

import org.apache.pekko.actor.typed.scaladsl._
import app.actor.Master.Command
import scala.concurrent.ExecutionContext

class Master(
    poolSize:         Int,
    workerBufferSize: Int,
    ctx:              ActorContext[Command],
    stashBuffer:      StashBuffer[Command]
)(implicit ec: ExecutionContext) {

  import Master._

  val pool: PoolRouter[Worker.Command] =
    Routers
      .pool(poolSize = poolSize) {
        Behaviors
          .supervise(Worker(workerBufferSize))
          .onFailure[Exception](SupervisorStrategy.restart)
      }
      .withRoundRobinRouting()

  val workers: ActorRef[Worker.Command] = ctx.spawn(pool, "worker-pool")

  val workerEventAdapter: ActorRef[Worker.Event] =
    ctx.messageAdapter[Worker.Event] { case ev: Worker.Event =>
      Command.HandleWorkerEvent(event = ev)
    }

  def ready(): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case Command.DoRequest(request, replyTo) if request.urls.length > 0 =>
        request.urls.foreach { uri =>
          workers ! Worker.Command.DoRequest(
            uri = uri,
            replyTo = workerEventAdapter
          )
        }

        busy(request.urls.length, List.empty[Title], replyTo)

      case Command.DoRequest(request, replyTo) =>
        replyTo ! Event.EventTitles(List.empty[Title])
        Behaviors.same[Command]

    }

  def busy(
      inProgress: Int,
      acc:        List[Title],
      replyTo:    ActorRef[Event]
  ): Behavior[Command] =
    Behaviors.receiveMessage {

      case cmd: Command.DoRequest =>
        stashBuffer.stash(cmd)
        Behaviors.same[Command]

      case Command.HandleWorkerEvent(Worker.Event.Err(uri, status, message)) =>
        val title: Title =
          Title(
            uri = uri,
            answer = Err(status = status, message = message)
          )

        handleTitle(inProgress, title, acc, replyTo)

      case Command.HandleWorkerEvent(Worker.Event.Ok(uri, value)) =>
        val title: Title =
          Title(
            uri = uri,
            answer = Ok(title = value)
          )

        handleTitle(inProgress, title, acc, replyTo)

    }

  def handleTitle(
      inProgress: Int,
      title:      Title,
      acc:        List[Title],
      replyTo:    ActorRef[Event]
  ): Behavior[Command] = {
    val newAcc:       List[Title] = title +: acc
    val newInProgess: Int         = inProgress - 1

    if (newInProgess > 0) {
      busy(newInProgess, newAcc, replyTo)
    } else {
      replyTo ! Event.EventTitles(newAcc)
      stashBuffer.unstashAll(ready())
    }
  }

}

object Master {
  sealed trait Command

  object Command {
    final case class DoRequest(request: UrlRequest, replyTo: ActorRef[Event])
        extends Command
    private[Master] final case class HandleWorkerEvent(event: Worker.Event)
        extends Command
  }

  sealed trait Event
  object Event {
    final case class EventTitles(titles: List[Title]) extends Event
  }

  def apply(poolSize: Int, bufferSize: Int)(implicit
      ec: ExecutionContext
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      Behaviors.withStash(bufferSize) { stashBuffer =>
        new Master(
          poolSize = poolSize,
          workerBufferSize = bufferSize,
          ctx = ctx,
          stashBuffer = stashBuffer
        ).ready()
      }

    }

}
