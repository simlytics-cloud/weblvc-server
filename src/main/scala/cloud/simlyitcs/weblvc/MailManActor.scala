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

object MailManActor {
  val MailManActorKey: ServiceKey[Command] = ServiceKey[Command]("mailManActor")

  def apply(): Behavior[Command] = {
    Behaviors.withStash(1000) { buffer =>
      Behaviors.setup[Command] { context =>
        context.log.info("Mailman Actor Starting")
        val listingResponseAdapter =
          context.messageAdapter[Receptionist.Listing](ListingResponse)

        context.system.receptionist ! Receptionist.Register(
          MailManActorKey,
          context.self
        )
        context.system.receptionist ! Receptionist.Subscribe(
          LoggingActor.LoggingActorKey,
          listingResponseAdapter
        )

        Behaviors.receiveMessage {
          case ListingResponse(
                LoggingActor.LoggingActorKey.Listing(listings)
              ) =>
            listings.headOption match {
              case Some(loggingActor) =>
                context.log.debug(
                  "Mailman actor received address of logging actor"
                )
                buffer.unstashAll(
                  addressBehavior(
                    Map[String, ActorRef[JsObject]](),
                    loggingActor
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

  private def addressBehavior(
      addressMap: Map[String, ActorRef[JsObject]],
      webLvcLogger: ActorRef[LoggingActor.Command]
  ): Behavior[Command] = {
    def sendLogMessage(clientName: String, message: String): Unit = {
      webLvcLogger ! LoggingActor.LoggingMessage(clientName, message)
    }
    Behaviors.receive { (context, message) =>
      message match {
        case AddClient(name, address) =>
          context.log.info(s"Adding $name to list of addressable clients")
          webLvcLogger ! LoggingActor.AddClient(name)
          addressBehavior(addressMap + (name -> address), webLvcLogger)
        case RemoveClient(name) =>
          context.log.info(s"Removing $name from list of addressable clients")
          addressBehavior(addressMap - name, webLvcLogger)
        case AddressedMessage(client, message) =>
          addressMap.get(client) match {
            case Some(address) =>
              context.log.debug(s"Sending message $message to $client")
              address ! message
              Behaviors.same
            case None =>
              val errorMessage =
                s"MailManActor cannot find address for client $client"
              context.log.error(errorMessage)
              webLvcLogger ! LoggingActor.LoggingMessage("", errorMessage)
              Behaviors.same
          }
        case ListingResponse(listing) =>
          listing match {
            case LoggingActor.LoggingActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(loggingActor) =>
                  context.log.debug(
                    "State Manager actor received updated address of mailman actor"
                  )
                  addressBehavior(addressMap, loggingActor)
                case None => Behaviors.same
              }
            case _ => Behaviors.same

          }
      }
    }
  }

  sealed trait Command

  case class AddClient(name: String, address: ActorRef[JsObject])
      extends Command

  case class RemoveClient(name: String) extends Command

  case class AddressedMessage(client: String, message: JsObject) extends Command

  case class ListingResponse(listing: Receptionist.Listing) extends Command
}
