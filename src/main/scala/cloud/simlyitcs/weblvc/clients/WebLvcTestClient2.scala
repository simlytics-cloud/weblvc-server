/*
 *     Web Live, Virtual, Constructive (WebLVC) Server
 *     Copyright (C) 2022  simlytics.cloud LLC and WebLVC Server contributors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package cloud.simlyitcs.weblvc.clients

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

object WebLvcTestClient2 extends App {
  val config = ConfigFactory.load("client_application.conf")

  implicit val system: ActorSystem = ActorSystem("SecondClient", config)
  import system.dispatcher
  connect
  // Set up client

  // print each incoming strict text message

  def connect = {
    val printSink: Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict =>
          println(s"Received from WebLVC server: ${message.text}")
        case _ =>
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
      Http().singleWebSocketRequest(
        WebSocketRequest(
          "ws://ec2-18-252-249-75.us-gov-east-1.compute.amazonaws.com:8082/weblvc/connect"
        ),
        flow
      )

    val connected = upgradeResponse.map { upgrade =>
      // just like a regular http request we can access response status which is available via upgrade.response.status
      // status code 101 (Switching Protocols) indicates that server support WebSockets

      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Done
      } else {
        throw new RuntimeException(
          s"Connection failed: ${upgrade.response.status}"
        )
      }
    }

    // in a real application you would not side effect here
    // and handle errors more carefully
    connected.onComplete {
      case Success(_)  => println("Connection completed")
      case Failure(ex) => println(s"Connection failed with exception: $ex")
    }
    closed.foreach(_ => println("Stream complete"))

    val connectMessage = TextMessage(
      scala.io.Source
        .fromResource("connect-message2.json")
        .getLines()
        .mkString("\n")
    )
    clientQueue.offer(connectMessage)

    Thread.sleep(30 * 1000)
    println("Sending StatusLogRequest")
    val statusLogRequestMessage = TextMessage(
      scala.io.Source
        .fromResource("status-log-request.json")
        .getLines()
        .mkString("\n")
    )
    clientQueue.offer(statusLogRequestMessage)

  }
}
