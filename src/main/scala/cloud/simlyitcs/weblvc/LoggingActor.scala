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

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import spray.json.JsObject

object LoggingActor {
  val LoggingActorKey: ServiceKey[Command] = ServiceKey[Command]("loggingActor")

  def apply(bufferSize: Int): Behavior[Command] = {
    Behaviors.withStash(1000) { buffer =>
      Behaviors.setup[Command] { context =>
        context.log.info("Logging actor starting")
        val listingResponseAdapter =
          context.messageAdapter[Receptionist.Listing](ListingResponse)

        context.system.receptionist ! Receptionist.Register(
          LoggingActorKey,
          context.self
        )
        context.system.receptionist ! Receptionist.Subscribe(
          MailManActor.MailManActorKey,
          listingResponseAdapter
        )

        Behaviors.receiveMessage {
          case ListingResponse(
                MailManActor.MailManActorKey.Listing(listings)
              ) =>
            listings.headOption match {
              case Some(mailmanActor) =>
                context.log.debug(
                  "Logging actor received address of Mailman actor"
                )
                val unknownLogger = context.spawn(
                  ClientLoggingActor("unknown", mailmanActor, bufferSize),
                  "unknownLogging"
                )
                buffer.unstashAll(
                  loggingBehavior(
                    Map[String, ActorRef[ClientLoggingActor.Command]](
                      "unknown" -> unknownLogger
                    ),
                    mailmanActor,
                    bufferSize
                  )
                )
              case None => Behaviors.same
            }
          case c: Command =>
            context.log.debug(s"Stashing message $c in buffer")
            buffer.stash(c)
            Behaviors.same
        }
      }
    }
  }

  def loggingBehavior(
      addressMap: Map[String, ActorRef[ClientLoggingActor.Command]],
      mailMan: ActorRef[MailManActor.Command],
      bufferSize: Int
  ): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case AddClient(name) =>
          if (addressMap.contains(name)) {
            val errorMessage =
              s"Attempted to add $name to list of logging clients, but was already added"
            context.log.debug(errorMessage)
            addressMap(name) ! ClientLoggingActor.LogMessage(errorMessage)
            Behaviors.same
          } else {
            context.log.info(s"Adding $name to list of logging actors")
            val clientLogger: ActorRef[ClientLoggingActor.Command] =
              context.spawn(
                ClientLoggingActor(name, mailMan, bufferSize),
                s"${name}Logging"
              )
            clientLogger ! ClientLoggingActor.LogMessage(
              "Created logging actor"
            )
            val updatedAddresses = addressMap + (name -> clientLogger)
            loggingBehavior(updatedAddresses, mailMan, bufferSize)
          }
        case RemoveClient(name) =>
          context.log.info(s"Removing $name from list of logging actors")
          loggingBehavior(addressMap - name, mailMan, bufferSize)
        case LoggingMessage(client, messageToLog) =>
          addressMap.get(client) match {
            case Some(address) =>
              context.log.debug(s"Sending message $messageToLog to $client")
              address ! ClientLoggingActor.LogMessage(messageToLog)
              Behaviors.same
            case None =>
              val errorMessage =
                s"Logging Actor cannot find address for client $client.  Dropping message $messageToLog"
              context.log.error(errorMessage)
              Behaviors.same
          }
        case ClientStatusLogRequest(client, statusLogRequest) =>
          addressMap.get(client) match {
            case Some(address) =>
              context.log.debug(
                s"Sending StatusLogRequest $statusLogRequest to $client"
              )
              address ! ClientLoggingActor.StatusLogRequestWrapper(
                statusLogRequest
              )
              Behaviors.same
            case None =>
              val errorMessage =
                s"Logging Actor cannot find address for client $client.  Dropping message $statusLogRequest"
              context.log.error(errorMessage)
              Behaviors.same
          }
        case ListingResponse(listing) =>
          listing match {
            case MailManActor.MailManActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(mailManActor) =>
                  context.log.debug(
                    "State Manager actor received updated address of mailman actor"
                  )
                  loggingBehavior(addressMap, mailManActor, bufferSize)
                case None => Behaviors.same
              }
            case _ => Behaviors.same
          }
      }
    }
  }

  sealed trait Command

  case class AddClient(name: String) extends Command

  case class RemoveClient(name: String) extends Command

  case class LoggingMessage(client: String, logMessage: String) extends Command

  case class ClientStatusLogRequest(client: String, statusLogRequest: JsObject)
      extends Command

  case class ListingResponse(listing: Receptionist.Listing) extends Command

}
