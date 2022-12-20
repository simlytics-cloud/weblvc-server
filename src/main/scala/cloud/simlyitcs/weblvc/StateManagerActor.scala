

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

object StateManagerActor extends DefaultJsonProtocol {
  val StateManagerActorKey: ServiceKey[Command] = ServiceKey[Command]("stateManagerActor")

  def apply(): Behavior[Command] = {
    setupBehavior(None, None)
  }

  private def setupBehavior(loggingActorOption: Option[ActorRef[LoggingActor.Command]],
                            timeManagerActorOption: Option[ActorRef[TimeManagerActor.Command]]): Behavior[Command] =
    Behaviors.withStash(1000) { buffer =>
      Behaviors.setup[Command] { context =>
        context.log.info("State Manager Actor Starting")
        val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](ListingResponse)
        context.system.receptionist ! Receptionist.Register(StateManagerActorKey, context.self)
        context.system.receptionist ! Receptionist.Subscribe(LoggingActor.LoggingActorKey, listingResponseAdapter)
        context.system.receptionist ! Receptionist.Subscribe(TimeManagerActor.TimeManagerActorKey,
          listingResponseAdapter)
        Behaviors.receiveMessage {
          case ListingResponse(LoggingActor.LoggingActorKey.Listing(listings)) =>
            listings.headOption match {
              case Some(loggingActor) =>
                context.log.debug("State Manager actor received address of logging actor")
                timeManagerActorOption match {
                  case Some(timeManagerActor) => buffer.unstashAll(stateManagerBehavior(Map(), Map(), Map(),
                    loggingActor, timeManagerActor))
                  case None => setupBehavior(Some(loggingActor), None)
                }
              case None => Behaviors.same
            }
          case ListingResponse(TimeManagerActor.TimeManagerActorKey.Listing(listings)) =>
            listings.headOption match {
              case Some(timeManagerActor) =>
                context.log.debug("State Manager actor received address of time manager actor")
                loggingActorOption match {
                  case Some(loggingActor) => buffer.unstashAll(stateManagerBehavior(Map(), Map(), Map(), loggingActor,
                    timeManagerActor))
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

  private def stateManagerBehavior(stateUpdateActorMap: Map[String, ActorRef[StateUpdateActor.Command]],
                                   objectTypeMap: Map[String, String],
                                   subscriptionMap: Map[String, Set[Subscription]],
                                   webLvcLogger: ActorRef[LoggingActor.Command],
                                   timeManager: ActorRef[TimeManagerActor.Command]
                                  ): Behavior[Command] = {
    def sendLogMessage(clientName: String, message: String): Unit = {
      webLvcLogger ! LoggingActor.LoggingMessage(clientName, message)
    }

    Behaviors.receive { (context, message) =>



      message match {
        case SubscribeObject(objectType: String, subscription: Subscription) =>
          val subscriber = subscription.subscriber
          context.log.info(s"Adding $subscriber to list of subscribers for $objectType")
          sendLogMessage(subscriber, s"Adding $subscriber to list of subscribers for $objectType")
          val currentSubscribers: Set[Subscription] = subscriptionMap.getOrElse(objectType, Set[Subscription]())
          val newSubscribers: Set[Subscription] = currentSubscribers + subscription
          val subscribers = subscriptionMap + (objectType -> newSubscribers)
          stateUpdateActorMap.get(objectType) match {
            case Some(stateUpdateActor) =>
              stateUpdateActor ! StateUpdateActor.GetAllUpdates(subscription.subscriber)
            case None =>
          }
          stateManagerBehavior(stateUpdateActorMap, objectTypeMap, subscribers, webLvcLogger, timeManager)
        case UnsubscribeObject(nameToUnsubscribe, objectType) =>
          context.log.info(s"Removing $nameToUnsubscribe to from of subscribers for $objectType")
          sendLogMessage(nameToUnsubscribe, s"Removing $nameToUnsubscribe from list of subscribers for $objectType")
          val currentSubscribers: Set[Subscription] = subscriptionMap.getOrElse(objectType, Set[Subscription]())
          val newSubscribers: Set[Subscription] = currentSubscribers.filterNot(s => s.subscriber == nameToUnsubscribe)
          val subscribers = subscriptionMap + (objectType -> newSubscribers)
          stateManagerBehavior(stateUpdateActorMap, objectTypeMap, subscribers, webLvcLogger, timeManager)
        case AttributeUpdate(client, attributeUpdate, uuid: String) =>
          val timestamp: Long = Timestamp.getTimestampFromJsObject(attributeUpdate) match {
            case Left(timestamp) => timestamp
            case Right(errorMessage) =>
              context.log.error(s"Timestamp error: $errorMessage from $client with attribute update: ${attributeUpdate.prettyPrint}")
              webLvcLogger ! LoggingActor.LoggingMessage(client, errorMessage)
              System.currentTimeMillis()
          }
          attributeUpdate.fields.get("ObjectName") match {
            case Some(jsValue) =>
              val objectName = jsValue.convertTo[String]
              val stateUpdate = StateUpdateActor.StateUpdate(client, timestamp, objectName, attributeUpdate, uuid)
              if (!attributeUpdate.fields.contains("Object")) {
                val errorMessage = s"Received attribute update $attributeUpdate from $client that has " +
                  s"no Object field.  Discarding"
                context.log.error(errorMessage)
                webLvcLogger ! LoggingActor.LoggingMessage(client, errorMessage)
                Behaviors.same
              }
              objectTypeMap.get(objectName) match {
                case Some(objectType) =>
                      stateUpdateActorMap.get(objectType) match {
                        case Some(stateUpdateActor) =>
                          context.log.debug(s"Received state update with id $uuid from $client for $objectName" +
                            s" at $timestamp")
                          stateUpdateActor ! stateUpdate
                          Behaviors.same
                        case None =>
                          val errorMessage = s"Received attribute update $attributeUpdate from $client that already " +
                            s"had an entry in objectNameMap but no actor in stateUpdateActorMap.  This should not " +
                            s"happen."
                          context.log.error(errorMessage)
                          webLvcLogger ! LoggingActor.LoggingMessage(client, errorMessage)
                          Behaviors.same
                      }

                case None =>
                  context.log.debug(s"Received state update with id $uuid from $client for $objectName at $timestamp")
                  attributeUpdate.fields.get("ObjectType") match {
                    case Some(jsValue) =>
                      val objectType = jsValue.convertTo[String]
                      val updatedObjectTypeMap = objectTypeMap + (objectName -> objectType)
                      stateUpdateActorMap.get(objectType) match {
                        case Some(stateUpdateActor) =>  // State update actor for this object type already exists
                          stateUpdateActor ! stateUpdate
                          stateManagerBehavior(stateUpdateActorMap, updatedObjectTypeMap, subscriptionMap, webLvcLogger,
                            timeManager)
                        case None =>
                          context.log.info(s"Starting state update actor for $objectName")
                          val stateUpdateActor = context.spawn(StateUpdateActor(
                            objectType, stateUpdate, context.self, uuid), s"${objectType}StateUpdateActor")
                          val updatedActors = stateUpdateActorMap + (objectType -> stateUpdateActor)
                          stateManagerBehavior(updatedActors, updatedObjectTypeMap, subscriptionMap, webLvcLogger,
                            timeManager)
                      }


                    case None =>
                      val errorMessage = s"Received attribute update $attributeUpdate from $client.  This is the  " +
                        s"first update for this object, but is has no \"ObjectType\" field.  Discarding"
                      context.log.error(errorMessage)
                      webLvcLogger ! LoggingActor.LoggingMessage(client, errorMessage)
                      Behaviors.same
                  }


              }

            case None =>
              val errorMessage = s"Received attribute update $attributeUpdate from $client with no value for " +
                s"'ObjectType'"
              context.log.error(errorMessage)
              webLvcLogger ! LoggingActor.LoggingMessage(client, errorMessage)
              Behaviors.same
          }
        case changesReply: ChangesReply =>
          subscriptionMap.get(changesReply.objectType).foreach { subscriptions =>
            subscriptions.filter(_.subscriber != changesReply.client).foreach { subscription =>
              if(subscription.filter(changesReply.fullState)) {
                context.log.debug(s"Sending state update of type ${changesReply.objectType} to " +
                  s"${subscription.subscriber}")
                if (changesReply.changesOnlyOption.isDefined) {
                  timeManager ! TimeManagerActor.TimeManagerMessage(subscription.subscriber,
                    changesReply.changesOnlyOption.get)
                }
              }
            }
          }
          context.log.debug(s"Completed state update for ${changesReply.uuid}")
          timeManager! TimeManagerActor.StateUpdateComplete(changesReply.uuid)
          Behaviors.same
        case changeReply: GetAllChangesReply =>
          subscriptionMap.get(changeReply.objectType).foreach { subscriptions =>
            subscriptions.find(_.subscriber == changeReply.client) match {
              case Some(subscription) =>
                if(subscription.filter(changeReply.latestState)) {
                  timeManager ! TimeManagerActor.TimeManagerMessage(subscription.subscriber, changeReply.latestState)
                }
              case None =>
                context.log.error(s"Got changes reply for ${changeReply.client} but could not find subscription")
            }
          }
          Behaviors.same
        case ListingResponse(listing) =>
          listing match {
            case TimeManagerActor.TimeManagerActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(timeManagerActor) =>
                  context.log.debug("State Manager actor received updated address of time manager actor")
                  stateManagerBehavior(stateUpdateActorMap, objectTypeMap, subscriptionMap, webLvcLogger,
                    timeManagerActor)
                case None => Behaviors.same
              }
            case LoggingActor.LoggingActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(loggingActor) =>
                  context.log.debug("State Manager actor received updated address of time manager actor")
                  stateManagerBehavior(stateUpdateActorMap, objectTypeMap, subscriptionMap, loggingActor, timeManager)
                case None => Behaviors.same
              }
            case _ => Behaviors.same

          }
      }
    }
  }

  sealed trait Command

  case class SubscribeObject(objectType: String, subscription: Subscription) extends Command

  case class UnsubscribeObject(name: String, objectType: String) extends Command

  case class AttributeUpdate(client: String, attributeUpdate: JsObject, uuid: String) extends Command

  case class ListingResponse(listing: Receptionist.Listing) extends Command

  case class ChangesReply(client: String, objectType: String, fullState: JsObject, changesOnlyOption: Option[JsObject],
                          uuid: String) extends Command

  case class GetAllChangesReply(client: String, objectType: String, latestState: JsObject) extends Command
}

