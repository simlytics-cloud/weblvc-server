package cloud.simlytics.weblvc

import akka.Done
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.scalatest._
import flatspec._
import matchers._

import scala.concurrent.Future
import scala.util.{Failure, Success}
/*
class WebSocketRoutesSpec extends AnyFlatSpec with should.Matchers with ScalatestRouteTest {

  val printSink: Sink[Message, Future[Done]] =
    Sink.foreach {
      case message: TextMessage.Strict =>
        println(s"Received from WebLVC server: ${message.text}")
    }

  val clientSourceOnly = Source
    .queue[Message](1000, OverflowStrategy.backpressure)

  val (clientQueue, clientSource) = clientSourceOnly.preMaterialize()

  // the Future[Done] is the materialized value of Sink.foreach
  // and it is completed when the stream completes
  val flow: Flow[Message, Message, Future[Done]] =
  Flow.fromSinkAndSourceMat(printSink, clientSource)(Keep.left)

  // upgradeResponse is a Future[WebSocketUpgradeResponse] that
  // completes or fails when the connection succeeds or fails
  // and closed is a Future[Done] representing the stream completion from above
  val (upgradeResponse, closed) =
  Http().singleWebSocketRequest(WebSocketRequest("ws://localhost:8080/connect"), flow)

  val connected = upgradeResponse.map { upgrade =>
    // just like a regular http request we can access response status which is available via upgrade.response.status
    // status code 101 (Switching Protocols) indicates that server support WebSockets
    "A WebLVC server" should {
      "upgrade the connection" in {
        upgrade.response.status shouldEqual StatusCodes.SwitchingProtocols
      }
    }
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  // in a real application you would not side effect here
  // and handle errors more carefully
  connected.onComplete {
    case Success(_) => println("Connection completed")
    case Failure(ex) => println(s"Connection failed with exception: $ex")
  }
  closed.foreach(_ => println("Stream complete"))


  "A WebLVC server" should {
    "respond to connect and configure message" in {
      val connectMessage = TextMessage(scala.io.Source.fromResource("connect-message2.json").getLines().mkString("\n"))
      clientQueue.offer(connectMessage)
    }
  }

  keepRunning()

  def keepRunning(): Unit = {
    while (true) {
      Thread.sleep(1000)
      clientQueue.offer(TextMessage("ping"))
    }
  }


  /*
  clientQueue.offer(TextMessage("Hello from client"))
  Thread.sleep(3000)
  clientQueue.offer(TextMessage("Update from client"))
  Thread.sleep(3000)
  clientQueue.complete()

 */


}
 */
