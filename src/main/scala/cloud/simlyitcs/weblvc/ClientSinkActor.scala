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

import akka.actor.typed.scaladsl.adapter._
import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Sink, SourceQueueWithComplete}
import cloud.simlyitcs.weblvc.TimeManagerActor.StartResume
import cloud.simlyitcs.weblvc.WebLvcGuardian.TypedActorsResponse
import spray.json._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ClientSinkActor {
  def props(
      clientAddress: String,
      sendQueue: SourceQueueWithComplete[Message],
      typedActors: TypedActorsResponse
  ): Props =
    Props(
      new ClientSinkActor(
        clientAddress,
        sendQueue,
        typedActors: TypedActorsResponse
      )
    )

  case class StreamFailed(client: String)

  case object StreamClosed
}

class ClientSinkActor(
    clientAddress: String,
    sendQueue: SourceQueueWithComplete[Message],
    typedActors: TypedActorsResponse
) extends Actor
    with DefaultJsonProtocol {
  implicit val actorSystem: ActorSystem = context.system
  private implicit val executionContext: ExecutionContext =
    context.system.dispatcher
  private val log = Logging(context.system, this)
  private var clientName: String = "unknown"
  private var timeStampFormat: Int = 0

  def sendLogMessage(message: String): Unit = {
    log.error(message)
    typedActors.loggingActor ! LoggingActor.LoggingMessage(clientName, message)
  }

  def sendWebLvcMessage(message: JsObject): Unit = {
    typedActors.mailManActor ! MailManActor.AddressedMessage(
      clientName,
      message
    )
  }

  def handleIncomingMessage(message: JsObject): Unit = {
    message.fields.get("MessageKind") match {
      case Some(jsString: JsString) =>
        val messageKind = jsString.convertTo[String]
        if (clientName == "unknown" && messageKind != "Connect") {
          sendLogMessage(
            s"Received message from client $message before getting a Connect message"
          )
        } else {
          messageKind match {

            case "Connect" =>
              if (clientName == "unknown") {
                log.info(s"Initiating client actor for $clientName")
                typedActors.loggingActor ! LoggingActor.LoggingMessage(
                  clientName,
                  s"Initiating client actors"
                )
                clientName = message.fields.get("ClientName") match {
                  case Some(jsValue) => jsValue.convertTo[String]
                  case None =>
                    sendLogMessage(
                      s"Received connect message $message with no client name, using ip: $clientAddress as client name"
                    )
                    clientAddress
                }
                val sendToClientActor: akka.actor.typed.ActorRef[JsObject] =
                  context.spawn(
                    SendToClientActor(
                      clientName,
                      sendQueue,
                      typedActors.loggingActor
                    ),
                    s"${clientName}SendToClient"
                  )
                typedActors.mailManActor ! MailManActor.AddClient(
                  clientName,
                  sendToClientActor
                )
                typedActors.loggingActor ! LoggingActor.AddClient(clientName)
                val connectResponse: JsObject = JsObject(
                  Map(
                    "MessageKind" -> JsString("ConnectResponse"),
                    "Connected" -> JsBoolean(true)
                  )
                )
                typedActors.mailManActor ! MailManActor.AddressedMessage(
                  clientName,
                  connectResponse
                )
                message.fields.get("Messages") match {
                  case Some(jsArray: JsArray) =>
                    jsArray.elements.foreach(jsValue =>
                      handleIncomingMessage(jsValue.asJsObject)
                    )
                  case _ =>
                }
              } else {
                sendLogMessage(
                  s"Received second connect message for $clientName, Ignoring"
                )
                typedActors.loggingActor ! LoggingActor.LoggingMessage(
                  clientName,
                  s"Received second connect message for $clientName,  Ignoring"
                )
              }
            case "Configure" =>
              val timeConstrained: Boolean =
                message.fields.get("TimeConstrained") match {
                  case Some(constrainedValue) =>
                    constrainedValue.convertTo[Boolean]
                  case None => false
                }
              val lookAheadOption: Option[Long] =
                message.fields.get("LookAhead") match {
                  case Some(lookAhead) => Some(lookAhead.convertTo[Long])
                  case None            => None
                }
              val eventStepped: Boolean =
                message.fields.get("EventStepped") match {
                  case Some(eventStepped) => eventStepped.convertTo[Boolean]
                  case None               => true
                }
              if (timeConstrained || lookAheadOption.isDefined) { // client will require time management
                val addTimeManagedClient = TimeManagerActor.AddClient(
                  clientName,
                  timeConstrained,
                  lookAheadOption,
                  eventStepped
                )
                typedActors.timeManagerActor ! addTimeManagedClient
              }
              message.fields.get("TimestampFormat") match {
                case Some(jsNumber: JsNumber) =>
                  val acceptedTimestampFormat = jsNumber.convertTo[Int] match {
                    case x: Int =>
                      if (x >= 0 && x <= 2) {
                        timeStampFormat = x
                        true
                      } else {
                        false
                      }
                  }
                  sendWebLvcMessage(
                    JsObject(
                      Map(
                        "MessageKind" -> JsString("ConfigureResponse"),
                        "TimestampFormat" -> JsBoolean(acceptedTimestampFormat)
                      )
                    )
                  )
                case Some(value) =>
                  val errorMessage =
                    s"$clientName sent Configure message $message with invalid value for TimestampFormat " + value
                  sendLogMessage(errorMessage)
                  sendWebLvcMessage(
                    JsObject(
                      Map(
                        "MessageKind" -> JsString("ConfigureResponse"),
                        "TimestampFormat" -> JsBoolean(false)
                      )
                    )
                  )
                case None =>
                  sendWebLvcMessage(
                    JsObject(
                      Map("MessageKind" -> JsString("ConfigureResponse"))
                    )
                  )
              }
              val logMessage = s"Processed configuration for $clientName"
              log.debug(logMessage)
              sendLogMessage(logMessage)
            case "SubscribeObject" =>
              message.fields.get("ObjectType") match {
                case Some(jsString: JsString) =>
                  typedActors.stateManagerActor ! StateManagerActor
                    .SubscribeObject(
                      jsString.convertTo[String],
                      Subscription(clientName, message)
                    )
                case Some(illegalValue) =>
                  val errorMessage =
                    s"$clientName sent SubscribeObject message $message with invalid value for 'ObjectType' " + illegalValue
                  sendLogMessage(errorMessage)
                case None =>
                  val errorMessage =
                    s"$clientName sent SubscribeObject message $message with no value for 'ObjectType'"
                  sendLogMessage(errorMessage)
              }
            case "AttributeUpdate" =>
              typedActors.timeManagerActor ! TimeManagerActor
                .TimeManagedStateUpdate(
                  StateManagerActor.AttributeUpdate(
                    clientName,
                    message,
                    java.util.UUID.randomUUID().toString
                  )
                )

            case "UnsubscribeObject" =>
              message.fields.get("ObjectType") match {
                case Some(jsString: JsString) =>
                  typedActors.stateManagerActor ! StateManagerActor
                    .UnsubscribeObject(clientName, jsString.convertTo[String])
                case Some(illegalValue) =>
                  val errorMessage =
                    s"$clientName sent UnsubscribeObject message $message with invalid value for 'ObjectType' " + illegalValue
                  sendLogMessage(errorMessage)
                case None =>
                  val errorMessage =
                    s"$clientName sent UnsubscribeObject message $message with no value for 'ObjectType'"
                  sendLogMessage(errorMessage)
              }
            case "SubscribeInteraction" =>
              message.fields.get("InteractionType") match {
                case Some(jsString: JsString) =>
                  typedActors.interactionManagerActor ! InteractionManagerActor
                    .SubscribeInteraction(
                      jsString.convertTo[String],
                      Subscription(clientName, message)
                    )
                case Some(illegalValue) =>
                  val errorMessage =
                    s"$clientName sent SubscribeInteraction message $message with illegal value for 'InteractionType': " + illegalValue
                  sendLogMessage(errorMessage)
                case None =>
                  val errorMessage =
                    s"$clientName sent SubscribeInteraction message $message with no value for 'InteractionType'"
                  sendLogMessage(errorMessage)
              }
            case "Interaction" =>
              message.fields.get("InteractionType") match {
                case Some(jsString: JsString) =>
                  log.debug(s"Received interaction of type ${jsString.value}")
                  typedActors.timeManagerActor ! TimeManagerActor
                    .TimeManagedInteraction(
                      InteractionManagerActor.Interaction(
                        clientName,
                        jsString.convertTo[String],
                        message,
                        java.util.UUID.randomUUID().toString
                      )
                    )
                case Some(illegalValue) =>
                  val errorMessage =
                    s"$clientName sent Interaction message $message with illegal value for 'InteractionType': " + illegalValue
                  sendLogMessage(errorMessage)
                case None =>
                  val errorMessage =
                    s"$clientName sent Interaction message $message with no value for 'InteractionType'"
                  sendLogMessage(errorMessage)
              }
            case "UnsubscribeInteraction" =>
              message.fields.get("ObInteractionTypejectType") match {
                case Some(jsString: JsString) =>
                  typedActors.interactionManagerActor ! InteractionManagerActor
                    .UnsubscribeInteraction(
                      clientName,
                      jsString.convertTo[String]
                    )
                case Some(illegalValue) =>
                  val errorMessage =
                    s"$clientName sent UnsubscribeInteraction message $message with illegal value for 'InteractionType': " + illegalValue
                  sendLogMessage(errorMessage)
                case None =>
                  val errorMessage =
                    s"$clientName sent UnsubscribeInteraction message $message with no value for 'InteractionType'"
                  sendLogMessage(errorMessage)
              }
            case "StatusLogRequest" =>
              typedActors.loggingActor ! LoggingActor.ClientStatusLogRequest(
                clientName,
                message
              )
            case "StartResume" =>
              typedActors.timeManagerActor ! message.convertTo[StartResume]
            case "TimeAdvanceRequest" =>
              typedActors.timeManagerActor ! message
                .convertTo[TimeManagerActor.TimeAdvanceRequest]
            case "NextEventRequest" =>
              typedActors.timeManagerActor ! message
                .convertTo[TimeManagerActor.NextEventRequest]
            case _ =>
              val errorMessage =
                s"$clientName sent message $message with unrecognized 'MessageKind' field $messageKind"
              sendLogMessage(errorMessage)
          }
        }
      case Some(illegalValue) =>
        val errorMessage =
          s"$clientName sent message $message with illegal value in  'MessageKind' field: " + illegalValue
        sendLogMessage(errorMessage)
      case None =>
        val errorMessage =
          s"$clientName sent message $message with no 'MessageKind' field"
        sendLogMessage(errorMessage)
    }

  }

  def receive: Receive = {
    case tm: TextMessage =>
      val strictMessageFuture = tm.toStrict(3 seconds)
      strictMessageFuture.onComplete {
        case Success(message) =>
          val messageText = message.getStrictText

          if (messageText.equals("heartbeat")) {
            log.debug(s"Received heartbeat from " + clientName)
          } else {
            log.debug(
              s"Received WebLvcMessage from $clientName: $messageText"
            )
            try {
              val jsObject: JsObject = messageText.parseJson.asJsObject
              handleIncomingMessage(jsObject)
            } catch {
              case e: Exception =>
                val errorMessage =
                  s"Could not parse input from $clientName: ${e.getMessage}"
                sendLogMessage(errorMessage)
            }
          }
        case Failure(exception) =>
          println(s"Failed completing message to strict: $exception")
      }

    case bm: BinaryMessage =>
      // Drain the message to prevent clogging the stream
      bm.dataStream.runWith(Sink.ignore)
      println("Error: Received binary message on WebLvcSocket")

    case ClientSinkActor.StreamClosed =>
      log.info(s"Web socket stream for $clientName closed.")
      context.stop(self)

    case f: Throwable =>
      log.error(s"Web socket for $clientName threw and error")
      log.error(f.toString)
      context.stop(self)
  }
}
