package app.actor

import app.mock.MockServerEndpoints
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.mockserver.integration.ClientAndServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

class WorkerTestSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {

  val testKit = ActorTestKit("WorkerTests")

  val ports = 7771.until(7790).map(Integer.valueOf).toList

  val mockServer = ClientAndServer.startClientAndServer(ports: _*)

  val chosenPort = mockServer.getPort()

  override protected def beforeAll(): Unit =
    MockServerEndpoints.mkEndpoints(mockCS = mockServer)

  val fixedEC: ExecutionContext =
    ExecutionContext.fromExecutorService(
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    )

  "A Worker" when {
    "DoRequest call" should {

      val instance: ActorRef[Worker.Command] =
        testKit.spawn(
          Worker.apply(
            bufferSize = 10,
            followLocation = false,
            followMaxHop = 0
          )(fixedEC)
        )

      "return Ok for /index.html" in {
        val probe: TestProbe[Worker.Event] =
          testKit.createTestProbe[Worker.Event]()

        instance ! Worker.Command.DoRequest(mkMockUri("index.html"), probe.ref)

        probe.expectMessageType[Worker.Event.Ok]

      }

      "return Err for /no-title.html" in {
        val probe: TestProbe[Worker.Event] =
          testKit.createTestProbe[Worker.Event]()

        instance ! Worker.Command.DoRequest(
          mkMockUri("no-title.html"),
          probe.ref
        )

        probe.expectMessageType[Worker.Event.Err]

      }

      "return Err for /wrong-data" in {
        val probe: TestProbe[Worker.Event] =
          testKit.createTestProbe[Worker.Event]()

        instance ! Worker.Command.DoRequest(
          mkMockUri("wrong-data"),
          probe.ref
        )

        probe.expectMessageType[Worker.Event.Err]

      }

      "return Err for /not-found" in {
        val probe: TestProbe[Worker.Event] =
          testKit.createTestProbe[Worker.Event]()

        instance ! Worker.Command.DoRequest(
          mkMockUri("not-found"),
          probe.ref
        )

        probe.expectMessageType[Worker.Event.Err]

      }

    }

    "DoRequest call with follow location" should {

      val instance: ActorRef[Worker.Command] =
        testKit.spawn(
          Worker.apply(
            bufferSize = 10,
            followLocation = true,
            followMaxHop = 5
          )(fixedEC)
        )

      "return Ok for /follow-location" in {
        val probe: TestProbe[Worker.Event] =
          testKit.createTestProbe[Worker.Event]()

        instance ! Worker.Command.DoRequest(
          mkMockUri("follow-location"),
          probe.ref
        )

        probe.expectMessageType[Worker.Event.Ok]

      }

      "return Err for /follow-location-bad" in {
        val probe: TestProbe[Worker.Event] =
          testKit.createTestProbe[Worker.Event]()

        instance ! Worker.Command.DoRequest(
          mkMockUri("follow-location-bad"),
          probe.ref
        )

        probe.expectMessageType[Worker.Event.Err]

      }
    }

  }

  def mkMockUri(suffix: String) =
    s"http://localhost:$chosenPort/$suffix"

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    if (mockServer.isRunning()) {
      mockServer.stop()
    }
  }

}
