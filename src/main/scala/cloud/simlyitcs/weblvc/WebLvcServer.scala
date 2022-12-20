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

package cloud.simlyitcs.weblvc

import akka.AkkaException
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

object WebLvcServer extends App {
  val config = ConfigFactory.load()

  implicit val system: ActorSystem = ActorSystem("WebLVC", config)
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher
  val typedSystem = system.toTyped
  implicit val timeout: Timeout = Timeout(3 seconds)
  println("Log level is " + system.settings.LogLevel)

  val guardian = system.actorOf(Props[WebLvcGuardian](), "guardian")
  val typedActors = Await.result(
    (guardian ? WebLvcGuardian.GetTypedActors)
      .mapTo[WebLvcGuardian.TypedActorsResponse],
    5 seconds
  )

  typedActors.loggingActor ! LoggingActor.LoggingMessage(
    "unknown",
    "testMessage"
  )

  val prefix = config.getString("weblvc.prefix")
  val protocol = config.getString("weblvc.protocol")
  val interface = config.getString("weblvc.interface")
  val port = config.getInt("weblvc.port")

  def getConfig = config

  def webLvcRoute =
    extractRequest { r =>
      extractLog { logger =>
        logger.info(r.method.toString)
        logger.info(r.uri.toString)
        concat(
          path(prefix / "connect") {
            extractWebSocketUpgrade { upgrade =>
              extractClientIP { clientIp =>
                val queueSource =
                  Source.queue[Message](1000, OverflowStrategy.backpressure)
                val (sendQueue, clientSource) = queueSource.preMaterialize()
                val clientAddress = clientIp.toOption match {
                  case Some(inetAddress) =>
                    if (inetAddress.getHostName.nonEmpty) {
                      inetAddress.getHostName
                    } else {
                      inetAddress.getHostAddress
                    }
                  case None => "UnknownHost"
                }
                println(
                  s"Client at address $clientAddress connecting to WebLvc"
                )
                val clientSinkActor = system.actorOf(
                  ClientSinkActor.props(clientAddress, sendQueue, typedActors)
                )
                val sink = Sink.actorRef(
                  clientSinkActor,
                  ClientSinkActor.StreamClosed,
                  (ex: Throwable) => "Web Socket threw and error: " + ex
                )
                complete(
                  upgrade.handleMessagesWithSinkSource(sink, clientSource)
                )
              }
            }
          },
          path(prefix / "hello") {
            get {
              complete(
                HttpEntity(
                  ContentTypes.`text/html(UTF-8)`,
                  "<h1>Say hello to akka-http</h1>"
                )
              )
            }
          }
        )
      }
    }

  protocol match {
    case "ws" => Http().newServerAt(interface, port).bindFlow(webLvcRoute)
    case "wss" =>
      Http()
        .newServerAt(interface, port)
        .enableHttps(HttpsContext.httpsConnectionContext)
        .bindFlow(webLvcRoute)
    case _ =>
      throw new AkkaException(
        "Illegal websocket protocol in config: " + protocol
      )
  }

}
