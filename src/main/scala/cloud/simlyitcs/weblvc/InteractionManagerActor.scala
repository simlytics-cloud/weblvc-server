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
import spray.json.{DefaultJsonProtocol, JsObject}

object InteractionManagerActor extends DefaultJsonProtocol {
  val InteractionManagerActorKey: ServiceKey[Command] =
    ServiceKey[Command]("interactionManagerActor")

  def apply(): Behavior[Command] = {
    setupBehavior(None, None)
  }

  private def setupBehavior(
      loggingActorOption: Option[ActorRef[LoggingActor.Command]],
      timeManagerActorOption: Option[ActorRef[TimeManagerActor.Command]]
  ): Behavior[Command] =
    Behaviors.withStash(1000) { buffer =>
      Behaviors.setup[Command] { context =>
        context.log.info("Interaction Manager Actor Starting")
        val listingResponseAdapter =
          context.messageAdapter[Receptionist.Listing](ListingResponse)
        context.system.receptionist ! Receptionist.Register(
          InteractionManagerActorKey,
          context.self
        )
        context.system.receptionist ! Receptionist.Subscribe(
          LoggingActor.LoggingActorKey,
          listingResponseAdapter
        )
        context.system.receptionist ! Receptionist.Subscribe(
          TimeManagerActor.TimeManagerActorKey,
          listingResponseAdapter
        )
        Behaviors.receiveMessage {
          case ListingResponse(
                LoggingActor.LoggingActorKey.Listing(listings)
              ) =>
            listings.headOption match {
              case Some(loggingActor) =>
                context.log.debug(
                  "Interaction Manager actor received address of logging actor"
                )
                timeManagerActorOption match {
                  case Some(timeManagerActor) =>
                    buffer.unstashAll(
                      interactionManagerBehavior(
                        Map(),
                        loggingActor,
                        timeManagerActor
                      )
                    )
                  case None => setupBehavior(Some(loggingActor), None)
                }
              case None => Behaviors.same
            }
          case ListingResponse(
                TimeManagerActor.TimeManagerActorKey.Listing(listings)
              ) =>
            listings.headOption match {
              case Some(timeManagerActor) =>
                context.log.debug(
                  "Interaction Manager actor received address of time manager actor"
                )
                loggingActorOption match {
                  case Some(loggingActor) =>
                    buffer.unstashAll(
                      interactionManagerBehavior(
                        Map(),
                        loggingActor,
                        timeManagerActor
                      )
                    )
                  case None => setupBehavior(None, Some(timeManagerActor))
                }
              case None => Behaviors.same
            }
          case ListingResponse(_) => Behaviors.same
          case c: Command =>
            context.log.debug(s"Stashing message $c in buffer")
            buffer.stash(c)
            Behaviors.same
        }
      }
    }

  private def interactionManagerBehavior(
      subscriptionMap: Map[String, Set[Subscription]],
      webLvcLogger: ActorRef[LoggingActor.Command],
      timeManager: ActorRef[TimeManagerActor.Command]
  ): Behavior[Command] = {
    def sendLogMessage(clientName: String, message: String): Unit = {
      webLvcLogger ! LoggingActor.LoggingMessage(clientName, message)
    }

    Behaviors.receive { (context, message) =>
      message match {
        case SubscribeInteraction(
              interactionType: String,
              subscription: Subscription
            ) =>
          val subscriber = subscription.subscriber
          context.log.info(
            s"Adding $subscriber to list of subscribers for $interactionType"
          )
          sendLogMessage(
            subscriber,
            s"Adding $subscriber to list of subscribers for $interactionType"
          )
          val currentSubscribers: Set[Subscription] =
            subscriptionMap.getOrElse(interactionType, Set[Subscription]())
          val newSubscribers: Set[Subscription] =
            currentSubscribers + subscription
          val subscribers =
            subscriptionMap + (interactionType -> newSubscribers)
          interactionManagerBehavior(subscribers, webLvcLogger, timeManager)
        case UnsubscribeInteraction(nameToUnscbscribe, interactionType) =>
          context.log.info(
            s"Removing $nameToUnscbscribe to from of subscribers for $interactionType"
          )
          sendLogMessage(
            nameToUnscbscribe,
            s"Removing $nameToUnscbscribe from list of subscribers for $interactionType"
          )
          val currentSubscribers: Set[Subscription] =
            subscriptionMap.getOrElse(interactionType, Set[Subscription]())
          val newSubscribers: Set[Subscription] =
            currentSubscribers.filterNot(s => s.subscriber == nameToUnscbscribe)
          val subscribers =
            subscriptionMap + (interactionType -> newSubscribers)
          interactionManagerBehavior(subscribers, webLvcLogger, timeManager)
        case Interaction(client, interactionType, interaction, uuid) =>
          context.log.debug(
            s"Received interaction with id $uuid of type $interactionType from $client"
          )
          subscriptionMap.get(interactionType).foreach { subscriptions =>
            subscriptions.foreach { subscription =>
              if (subscription.filter(interaction)) {
                context.log.debug(
                  s"Sending interaction of type $interactionType to ${subscription.subscriber}"
                )
                timeManager ! TimeManagerActor
                  .TimeManagerMessage(subscription.subscriber, interaction)
              }
            }
            context.log.debug(s"Completed interaction for $uuid")
            timeManager ! TimeManagerActor.InteractionComplete(uuid)
          }
          Behaviors.same
        case ListingResponse(listing) =>
          listing match {
            case TimeManagerActor.TimeManagerActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(timeManagerActor) =>
                  context.log.debug(
                    "State Manager actor received updated address of time manager actor"
                  )
                  interactionManagerBehavior(
                    subscriptionMap,
                    webLvcLogger,
                    timeManagerActor
                  )
                case None => Behaviors.same
              }
            case LoggingActor.LoggingActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(loggingActor) =>
                  context.log.debug(
                    "State Manager actor received updated address of time manager actor"
                  )
                  interactionManagerBehavior(
                    subscriptionMap,
                    loggingActor,
                    timeManager
                  )
                case None => Behaviors.same
              }
            case _ => Behaviors.same

          }
      }
    }
  }

  sealed trait Command

  case class SubscribeInteraction(
      interactionType: String,
      subscription: Subscription
  ) extends Command

  case class UnsubscribeInteraction(name: String, interactionType: String)
      extends Command

  case class Interaction(
      client: String,
      interactionType: String,
      interaction: JsObject,
      uuid: String
  ) extends Command

  case class ListingResponse(listing: Receptionist.Listing) extends Command
}
