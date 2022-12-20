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
import cloud.simlyitcs.weblvc.MailManActor.AddressedMessage
import spray.json.JsObject

import scala.collection.immutable.TreeMap

/** This actor implements time management for connected clients using the same conventions used in
  * High Level Architecture (HLA) time management as implemented by the runtime infrastructure (RTI)
  */
object TimeManagerActor extends WebLvcJsonProtocol {
  val TimeManagerActorKey: ServiceKey[Command] =
    ServiceKey[Command]("timeManagerActor")

  def apply(): Behavior[Command] = {
    setupBehavior(None, None, None, None)
  }

  private def setupBehavior(
      loggingActorOption: Option[ActorRef[LoggingActor.Command]],
      mailManActorOption: Option[ActorRef[MailManActor.Command]],
      stateManagerOption: Option[ActorRef[StateManagerActor.Command]],
      interationManagerOption: Option[ActorRef[InteractionManagerActor.Command]]
  ): Behavior[Command] =
    Behaviors.withStash(1000) { buffer =>
      Behaviors.setup[Command] { context =>
        context.log.info("State Manager Actor Starting")
        val listingResponseAdapter =
          context.messageAdapter[Receptionist.Listing](ListingResponse)
        context.system.receptionist ! Receptionist.Register(
          TimeManagerActorKey,
          context.self
        )
        context.system.receptionist ! Receptionist.Subscribe(
          LoggingActor.LoggingActorKey,
          listingResponseAdapter
        )
        context.system.receptionist ! Receptionist.Subscribe(
          MailManActor.MailManActorKey,
          listingResponseAdapter
        )
        context.system.receptionist ! Receptionist.Subscribe(
          StateManagerActor.StateManagerActorKey,
          listingResponseAdapter
        )
        context.system.receptionist ! Receptionist.Subscribe(
          InteractionManagerActor.InteractionManagerActorKey,
          listingResponseAdapter
        )

        Behaviors.receiveMessage {
          case ListingResponse(
                LoggingActor.LoggingActorKey.Listing(listings)
              ) =>
            listings.headOption match {
              case Some(loggingActor) =>
                context.log.debug(
                  "Time Manager actor received address of logging actor"
                )
                if (
                  mailManActorOption.isDefined && stateManagerOption.isDefined && interationManagerOption.isDefined
                ) {
                  buffer.unstashAll(
                    timeManagerBehavior(
                      executing = false,
                      PendingMessages(List(), List()),
                      System.currentTimeMillis(),
                      Long.MaxValue,
                      Map(),
                      loggingActor,
                      mailManActorOption.get,
                      stateManagerOption.get,
                      interationManagerOption.get
                    )
                  )
                } else {
                  setupBehavior(
                    Some(loggingActor),
                    mailManActorOption,
                    stateManagerOption,
                    interationManagerOption
                  )
                }
              case None => Behaviors.same
            }
          case ListingResponse(
                MailManActor.MailManActorKey.Listing(listings)
              ) =>
            listings.headOption match {
              case Some(mailmanActor) =>
                context.log.debug(
                  "Time Manager actor received address of mailman actor"
                )
                if (
                  loggingActorOption.isDefined && stateManagerOption.isDefined && interationManagerOption.isDefined
                ) {
                  buffer.unstashAll(
                    timeManagerBehavior(
                      executing = false,
                      PendingMessages(List(), List()),
                      System.currentTimeMillis(),
                      Long.MaxValue,
                      Map(),
                      loggingActorOption.get,
                      mailmanActor,
                      stateManagerOption.get,
                      interationManagerOption.get
                    )
                  )
                } else {
                  setupBehavior(
                    loggingActorOption,
                    Some(mailmanActor),
                    stateManagerOption,
                    interationManagerOption
                  )
                }
              case None => Behaviors.same
            }
          case ListingResponse(
                StateManagerActor.StateManagerActorKey.Listing(listings)
              ) =>
            listings.headOption match {
              case Some(stateManagerActor) =>
                context.log.debug(
                  "Time Manager actor received address of state manager actor"
                )
                if (
                  loggingActorOption.isDefined && mailManActorOption.isDefined && interationManagerOption.isDefined
                ) {
                  buffer.unstashAll(
                    timeManagerBehavior(
                      executing = false,
                      PendingMessages(List(), List()),
                      System.currentTimeMillis(),
                      Long.MaxValue,
                      Map(),
                      loggingActorOption.get,
                      mailManActorOption.get,
                      stateManagerActor,
                      interationManagerOption.get
                    )
                  )
                } else {
                  setupBehavior(
                    loggingActorOption,
                    mailManActorOption,
                    Some(stateManagerActor),
                    interationManagerOption
                  )
                }
              case None => Behaviors.same
            }

          case ListingResponse(
                InteractionManagerActor.InteractionManagerActorKey.Listing(
                  listings
                )
              ) =>
            listings.headOption match {
              case Some(interactionManagerActor) =>
                context.log.debug(
                  "Time Manager actor received address of interaction manager actor"
                )
                if (
                  loggingActorOption.isDefined && mailManActorOption.isDefined && stateManagerOption.isDefined
                ) {
                  buffer.unstashAll(
                    timeManagerBehavior(
                      executing = false,
                      PendingMessages(List(), List()),
                      System.currentTimeMillis(),
                      Long.MaxValue,
                      Map(),
                      loggingActorOption.get,
                      mailManActorOption.get,
                      stateManagerOption.get,
                      interactionManagerActor
                    )
                  )
                } else {
                  setupBehavior(
                    loggingActorOption,
                    mailManActorOption,
                    stateManagerOption,
                    Some(interactionManagerActor)
                  )
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

  private def timeManagerBehavior(
      executing: Boolean,
      pendingMessages: PendingMessages,
      simulationStartTime: Long,
      simulationStopTime: Long,
      nextTimeMap: Map[String, ClientData],
      webLvcLogger: ActorRef[LoggingActor.Command],
      mailMan: ActorRef[MailManActor.Command],
      stateManager: ActorRef[StateManagerActor.Command],
      interactionManager: ActorRef[InteractionManagerActor.Command]
  ): Behavior[Command] = {
    def sendLogMessage(clientName: String, message: String): Unit = {
      webLvcLogger ! LoggingActor.LoggingMessage(clientName, message)
    }

    Behaviors.receive { (context, message) =>
      def updateLowerBoundTimeConstraints(
          nextTimeMap: Map[String, ClientData]
      ): Map[String, ClientData] = {

        nextTimeMap map { case (client, clientData) =>
          client -> {
            // Get the minimum lower bound time stamp from all other regulating federates
            val lowerBoundTimeConstraintOption: Option[Long] =
              clientData.lowerBoundTimeConstraint.map { _ =>
                val timeRegulatingFederateTimeConstraints: Iterable[Long] =
                  nextTimeMap.view
                    .filterKeys(_ != client)
                    .values
                    .filter(_.lookAhead.isDefined)
                    .map(ct => ct.clientLogicalTime + ct.lookAhead.get)
                if (timeRegulatingFederateTimeConstraints.nonEmpty) {
                  timeRegulatingFederateTimeConstraints.min
                } else {
                  simulationStopTime
                }
              }
            clientData.copy(lowerBoundTimeConstraint =
              lowerBoundTimeConstraintOption
            )
          }
        }

      }

      /** In the case where all clients are time constrained, WebLvc must advance the simulation.  once the WebLvc
        * Server has a next event request from each of them, it will grant a time advance to the least value.
        * @param currentNextTimeMap An updated map of ClientData for each federate
        * @return An updated next time map with current time and lower bound time constraints adjusted for the time advance
        */
      def advanceToNextEvent(
          currentNextTimeMap: Map[String, ClientData]
      ): Map[String, ClientData] = {
        // Give initial time advance grants once all requests are in for time constrained clients
        val timeConstrainedFederates = currentNextTimeMap.filter {
          case (_, clientData) =>
            clientData.lowerBoundTimeConstraint.isDefined
        }

        if (
          timeConstrainedFederates.nonEmpty &&
          // If all federates are time constrained
          timeConstrainedFederates.size == currentNextTimeMap.size
          // and all time constrained federates have a pending advance
          && timeConstrainedFederates.values
            .map(_.pendingAdvance.isDefined)
            .forall(_ == true)
        ) {
          val advanceTime: Long = timeConstrainedFederates.values.map {
            clientData =>
              val clientAdvanceTime: Long =
                if (clientData.timeOrderQueue.isEmpty) {
                  clientData.pendingAdvance.get
                } else {
                  List(
                    clientData.pendingAdvance.get,
                    clientData.timeOrderQueue.firstKey
                  ).min
                }
              clientAdvanceTime
          }.min
          val timeAdvanceUpdate = currentNextTimeMap.map {
            case (client, clientData) =>
              if (clientData.lowerBoundTimeConstraint.isDefined) {
                client -> clientData.copy(clientLogicalTime = advanceTime)
              } else {
                client -> clientData
              }
          }
          timeAdvanceUpdate
        } else {
          currentNextTimeMap
        }
      }

      def addMessageToQueue(
          addressedMessage: AddressedMessage,
          clientData: ClientData
      ): ClientData = {
        Timestamp.getTimestampFromJsObject(addressedMessage.message) match {
          case Left(
                time
              ) => // Timestamped message is added to time ordered queue
            val messagesAtTime = clientData.timeOrderQueue.get(time) match {
              case Some(messageArray) => messageArray :+ addressedMessage
              case None               => List(addressedMessage)
            }
            clientData.copy(timeOrderQueue =
              clientData.timeOrderQueue + (time -> messagesAtTime)
            )
          case Right(
                message
              ) => //  Message without timestamp is added to receiveOrderQueue
            context.log.debug(
              s"Did not get timestamp in $addressedMessage because $message"
            )
            clientData.copy(receiveOrderQueue =
              clientData.receiveOrderQueue :+ addressedMessage
            )
        }
      }

      /** This method takes in the latest client data, updates the lower bound time constraint for each federate,
        * and grants all time requests that can be granted based on the state of the client
        * @param updatedExecuting whether the simulation is running
        * @param updatedMap the latest nextTimeMap updated with data from a just-received message
        * @return the updated state data associated with this behavior, including the updated value for executing
        *         and the client data updated based on changes to lower bound tine constraints and time advance grants.
        */
      def executeSimulation(
          updatedExecuting: Boolean,
          updatedMap: Map[String, ClientData],
          updatedPendingMessages: PendingMessages
      ): Behavior[Command] = {
        // Don't execute simulation until all pending interactions and state updates have been received
        if (!updatedPendingMessages.isEmpty) {
          context.log.debug(
            s"in executeSimulation, Simulation is paused due to pending messages $pendingMessages.  Holding off execution."
          )
          timeManagerBehavior(
            updatedExecuting,
            updatedPendingMessages,
            simulationStartTime,
            simulationStopTime,
            updatedMap,
            webLvcLogger,
            mailMan,
            stateManager,
            interactionManager
          )
        } else {

          // Update lower bound time constraints for each client
          val timeAdvanceUpdates = advanceToNextEvent(updatedMap)
          val timeConstraintUpdates =
            updateLowerBoundTimeConstraints(timeAdvanceUpdates)

          // Update the nextTimeMap and associated client data by checking to see if each client can advance
          if (updatedExecuting) {
            val fullyUpdatedMap: Map[String, ClientData] =
              timeConstraintUpdates map {
                case (client, clientData) => // for each client
                  client -> advanceTimeConstrainedFederate(client, clientData)
              }
            timeManagerBehavior(
              updatedExecuting,
              updatedPendingMessages,
              simulationStartTime,
              simulationStopTime,
              fullyUpdatedMap,
              webLvcLogger,
              mailMan,
              stateManager,
              interactionManager
            )
          } else {
            context.log.debug(
              s"Simulation is not executing yet, so no time advances will be granted."
            )
            timeManagerBehavior(
              updatedExecuting,
              updatedPendingMessages,
              simulationStartTime,
              simulationStopTime,
              timeConstraintUpdates,
              webLvcLogger,
              mailMan,
              stateManager,
              interactionManager
            )
          }
        }
      }

      def advanceTimeConstrainedFederate(
          client: String,
          clientData: ClientData
      ): ClientData = {
        if (clientData.clientLogicalTime >= simulationStopTime) {
          context.log.debug(
            s"Client $client has reached simulation stop time.  Not advancing"
          )
          clientData
        } else {
          clientData.pendingAdvance match {
            case None =>
              context.log.debug(
                s"Client $client at time ${clientData.clientLogicalTime} has no pending advance.  Not advancing"
              )
              clientData
            case Some(pendingAdvance) =>
              val receiveOrderAdvanceTimeOption: Option[Long] =
                if (clientData.receiveOrderQueue.isEmpty) {
                  None
                } else {
                  clientData.lowerBoundTimeConstraint
                }
              val timeOrderAdvanceTimeOption: Option[Long] =
                clientData.timeOrderQueue.headOption map {
                  case (messageTime, _) => messageTime
                }
              val minAdvanceTime: Long = List(
                Some(pendingAdvance),
                receiveOrderAdvanceTimeOption,
                timeOrderAdvanceTimeOption
              ).flatten.min
              val advanceTime: Long = if (clientData.eventStepped) {
                minAdvanceTime
              } else {
                pendingAdvance
              }
              if (advanceTime <= clientData.lowerBoundTimeConstraint.get) { // Advance the client
                clientData.receiveOrderQueue.foreach { addressedMessage =>
                  mailMan ! addressedMessage
                }

                // Send all messages in time order queue up to the advance time
                clientData.timeOrderQueue.view
                  .filterKeys(_ <= advanceTime)
                  .values
                  .foreach { messageList =>
                    messageList.foreach(addressedMessage =>
                      mailMan ! addressedMessage
                    )
                  }

                // Send the time advance grant to the client
                if (advanceTime < clientData.clientLogicalTime) {
                  throw new RuntimeException(
                    s"Advancing client $client to time $advanceTime in its past because $client is at ${clientData.clientLogicalTime}"
                  )
                }
                context.log.debug(s"Advancing client $client to $advanceTime")
                mailMan ! AddressedMessage(
                  client,
                  TimeAdvanceGranted(
                    "TimeAdvanceGranted",
                    advanceTime
                  ).toJson.asJsObject
                )

                // Remove the just granted time advance request from the client's data
                // Remove all messages from receive order queue
                // Remove sent messages from time order queue
                val updatedTimeOrderQueue
                    : TreeMap[Long, List[AddressedMessage]] =
                  clientData.timeOrderQueue.filter(_._1 > advanceTime)
                clientData.copy(
                  pendingAdvance = None,
                  receiveOrderQueue = List(),
                  timeOrderQueue = updatedTimeOrderQueue
                )
              } else { // No advance because minimum advance is past lower bound time constraint
                context.log.debug(
                  s"Client $client at time ${clientData.clientLogicalTime}, advance time of $advanceTime " +
                    s"is not less than lower bound time constraint of ${clientData.lowerBoundTimeConstraint.get}.  Not advancing"
                )
                clientData
              }

          }
        }
      }

      /** Adds a time advance request to a client's data. Logs an error if no client data exists for the named client
        * @param client the client
        * @param nextLogicalTime the time to which the client has requested an advance
        * @return the updated state of this behavior based on a call to executeSimulation
        */
      def clientAdvanceRequest(
          client: String,
          nextLogicalTime: Long
      ): Behavior[Command] = {
        nextTimeMap.get(client) match {
          case Some(clientData) =>
            val updatedClientData =
              clientData.copy(pendingAdvance = Some(nextLogicalTime))
            executeSimulation(
              executing,
              nextTimeMap + (client -> updatedClientData),
              pendingMessages
            )
          case None =>
            val errorMessage =
              s"Received NextSimulationTime from $client, but no client data was available"
            context.log.error(errorMessage)
            sendLogMessage(client, errorMessage)
            Behaviors.same
        }
      }

      message match {

        case AddClient(client, timeConstrained, lookAhead, eventStepped) =>
          val initialLowerBoundTimeConstraint = if (timeConstrained) {
            Some(-1L)
          } else {
            None
          }
          val clientData: ClientData = ClientData(
            initialLowerBoundTimeConstraint,
            lookAhead,
            eventStepped,
            0L,
            None,
            List(),
            TreeMap()
          )
          val logMessage =
            s"Adding $client to list of time managed simulations with client data $clientData"
          context.log.info(logMessage)
          sendLogMessage(client, logMessage)
          val updatedNextTimeMap = nextTimeMap + (client -> clientData)
          timeManagerBehavior(
            executing,
            pendingMessages,
            simulationStartTime,
            simulationStopTime,
            updatedNextTimeMap,
            webLvcLogger,
            mailMan,
            stateManager,
            interactionManager
          )
        case RemoveClient(client) =>
          val logMessage =
            s"Removing client $client from list of time managed simulations"
          context.log.info(logMessage)
          sendLogMessage(client, logMessage)
          executeSimulation(executing, nextTimeMap - client, pendingMessages)
        case sr: StartResume =>
          val simulationStop = sr.AdvanceTime match {
            case Some(advanceTime) => advanceTime
            case None              => Long.MaxValue
          }
          if (!executing) { // simulation has not started
            // Set the logical time for each client to the simulation start time
            val updatedNextTimeMap = nextTimeMap.map {
              case (client, clientData) =>
                client -> clientData.copy(clientLogicalTime =
                  sr.SimulationStartTime
                )
            }
            nextTimeMap.keys.foreach { client =>
              mailMan ! MailManActor.AddressedMessage(
                client,
                StartResume(
                  "StartResume",
                  client,
                  IncrementalValues.nextRequestIdentifer,
                  System.currentTimeMillis(),
                  sr.SimulationStartTime,
                  Some(simulationStop)
                ).toJson.asJsObject
              )
            }
            timeManagerBehavior(
              executing = true,
              pendingMessages,
              sr.SimulationStartTime,
              simulationStop,
              updatedNextTimeMap,
              webLvcLogger,
              mailMan,
              stateManager,
              interactionManager
            )
          } else { // resume
            executeSimulation(
              updatedExecuting = true,
              nextTimeMap,
              pendingMessages
            )
          }

        case TimeAdvanceRequest(_, client, nextLogicalTime) =>
          clientAdvanceRequest(client, nextLogicalTime)

        case NextEventRequest(_, client, nextLogicalTime) =>
          clientAdvanceRequest(client, nextLogicalTime)

        case _: StopFreeze =>
          timeManagerBehavior(
            executing = false,
            pendingMessages,
            simulationStartTime,
            simulationStopTime,
            nextTimeMap,
            webLvcLogger,
            mailMan,
            stateManager,
            interactionManager
          )

        case timeManagerMessage: TimeManagerMessage =>
          nextTimeMap.get(timeManagerMessage.client) match {
            case Some(clientData) =>
              val updatedClientData = addMessageToQueue(
                timeManagerMessage.toAddressedMessage,
                clientData
              )
              timeManagerBehavior(
                executing,
                pendingMessages,
                simulationStartTime,
                simulationStopTime,
                nextTimeMap + (timeManagerMessage.client -> updatedClientData),
                webLvcLogger,
                mailMan,
                stateManager,
                interactionManager
              )
            case None =>
              context.log.debug(
                s"Time manager received message for non-time managed client ${timeManagerMessage.client}.  Forwarding directly to client."
              )
              mailMan ! timeManagerMessage.toAddressedMessage
              Behaviors.same
          }

        case TimeManagedStateUpdate(
              attributeUpdate: StateManagerActor.AttributeUpdate
            ) =>
          context.log.debug(
            s"Sending state update with uuid ${attributeUpdate.uuid} to state manager. Pausing execution"
          )
          stateManager ! attributeUpdate
          val updatedPendingMessages = pendingMessages.copy(stateUpdateIds =
            attributeUpdate.uuid :: pendingMessages.stateUpdateIds
          )
          timeManagerBehavior(
            executing,
            updatedPendingMessages,
            simulationStartTime,
            simulationStopTime,
            nextTimeMap,
            webLvcLogger,
            mailMan,
            stateManager,
            interactionManager
          )

        case TimeManagedInteraction(
              interaction: InteractionManagerActor.Interaction
            ) =>
          context.log.debug(
            s"Sending interaction with uuid ${interaction.uuid} to state manager.  Pausing execution"
          )
          interactionManager ! interaction
          val updatedPendingMessages = pendingMessages.copy(interactionIds =
            interaction.uuid :: pendingMessages.interactionIds
          )
          timeManagerBehavior(
            executing,
            updatedPendingMessages,
            simulationStartTime,
            simulationStopTime,
            nextTimeMap,
            webLvcLogger,
            mailMan,
            stateManager,
            interactionManager
          )

        case StateUpdateComplete(uuid) =>
          context.log.debug(s"State update for uuid $uuid is complete.")
          val updatedPendingMessages = pendingMessages.copy(stateUpdateIds =
            pendingMessages.stateUpdateIds.filterNot(_ == uuid)
          )
          if (updatedPendingMessages.isEmpty) {
            context.log.debug("Executing simulation again")
            executeSimulation(executing, nextTimeMap, updatedPendingMessages)
          } else {
            context.log.debug(
              s"Updates still pending: $updatedPendingMessages. Execution still paused"
            )
            timeManagerBehavior(
              executing,
              updatedPendingMessages,
              simulationStartTime,
              simulationStopTime,
              nextTimeMap,
              webLvcLogger,
              mailMan,
              stateManager,
              interactionManager
            )
          }

        case InteractionComplete(uuid) =>
          context.log.debug(s"Interaction for uuid $uuid is complete.")
          val updatedPendingMessages = pendingMessages.copy(interactionIds =
            pendingMessages.interactionIds.filterNot(_ == uuid)
          )
          if (updatedPendingMessages.isEmpty) {
            context.log.debug("Executing simulation again")
            executeSimulation(executing, nextTimeMap, updatedPendingMessages)
          } else {
            context.log.debug(
              s"Updates still pending: $updatedPendingMessages. Execution still paused."
            )
            timeManagerBehavior(
              executing,
              updatedPendingMessages,
              simulationStartTime,
              simulationStopTime,
              nextTimeMap,
              webLvcLogger,
              mailMan,
              stateManager,
              interactionManager
            )
          }

        case ListingResponse(listing) =>
          listing match {
            case MailManActor.MailManActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(mailManActor) =>
                  context.log.debug(
                    "Time Manager actor received updated address of mailman actor"
                  )
                  timeManagerBehavior(
                    executing,
                    pendingMessages,
                    simulationStartTime,
                    simulationStopTime,
                    nextTimeMap,
                    webLvcLogger,
                    mailManActor,
                    stateManager,
                    interactionManager
                  )
                case None => Behaviors.same
              }
            case LoggingActor.LoggingActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(loggingActor) =>
                  context.log.debug(
                    "Time Manager actor received updated address of logging actor"
                  )
                  timeManagerBehavior(
                    executing,
                    pendingMessages,
                    simulationStartTime,
                    simulationStopTime,
                    nextTimeMap,
                    loggingActor,
                    mailMan,
                    stateManager,
                    interactionManager
                  )
                case None => Behaviors.same
              }
            case StateManagerActor.StateManagerActorKey.Listing(listings) =>
              listings.headOption match {
                case Some(stateManagerActor) =>
                  context.log.debug(
                    "Time Manager actor received updated address of state manager actor"
                  )
                  timeManagerBehavior(
                    executing,
                    pendingMessages,
                    simulationStartTime,
                    simulationStopTime,
                    nextTimeMap,
                    webLvcLogger,
                    mailMan,
                    stateManagerActor,
                    interactionManager
                  )
                case None => Behaviors.same
              }
            case InteractionManagerActor.InteractionManagerActorKey.Listing(
                  listings
                ) =>
              listings.headOption match {
                case Some(interactionManagerActor) =>
                  context.log.debug(
                    "Time Manager actor received updated address of interaction manager actor"
                  )
                  timeManagerBehavior(
                    executing,
                    pendingMessages,
                    simulationStartTime,
                    simulationStopTime,
                    nextTimeMap,
                    webLvcLogger,
                    mailMan,
                    stateManager,
                    interactionManagerActor
                  )
                case None => Behaviors.same
              }
            case _ => Behaviors.same

          }

      }
    }
  }

  /** A marker trait for allowable messages to the TimeManagerActor
    */
  sealed trait Command

  /** This is the message sent to this TimeManagerActor by the StateManagerActor or InteractionManager.
    * If the addressee of the message is not time constrained, the message is immediately forwarded to
    * the MailManActor, which sends it to the client.  For time constrained messages, this message will be stored
    * in the client's receive order queue (for non timestamped messages) or time ordered queue (for timestamped
    * messages)
    * @param client the client recipient of the message
    * @param message the message to the client
    */
  case class TimeManagerMessage(client: String, message: JsObject)
      extends Command {
    def toAddressedMessage: AddressedMessage = AddressedMessage(client, message)
  }

  case class ListingResponse(listing: Receptionist.Listing) extends Command

  /** Message sent to add client to time managed simulation.  Clients can be added at any time.
    * @param name the name of the client
    * @param timeConstrained whether the client is time constrained
    * @param lookAhead for time regulating clients, this is the interval, calculated from the client's current time, before
    *                  which it agrees not to send any timestamped messages.  This allow parallel execution by other
    *                  time managed clients in this window.
    * @param eventStepped If true, the client advances one event at a time, regardless of time step.  If false, the client
    *                     advances by time steps.
    */
  case class AddClient(
      name: String,
      timeConstrained: Boolean,
      lookAhead: Option[Long],
      eventStepped: Boolean
  ) extends Command

  /** Message sent to remove a client from time management
    * @param name the name of the client to remove
    */
  case class RemoveClient(name: String) extends Command

  /** Message sent to start and resume simulation.  When sent to the TimeManagerActor, it starts execution of the simulation
    * by sending StartResume messages to each time constrained and time regulating client.  It then starts granting
    * time advances to these clients
    *
    * @param MessageKind Must be "StartResume" as per WebLVC standard
    * @param ReceivingEntity The entity to whom the message is sent
    * @param RequestIdentifier An increasing unique identifier for these messages.  Requuired by WebLVC standard.
    *                          It is not used by this implementation of WebLVC
    * @param RealWorldTime       The real world time of the message
    * @param SimulationStartTime A number representing the start time of the entire simulation (For many simulations,
    *                            this is the equivalent of 0 on the simulation clock
    * @param AdvanceTime         the time to which the simulation will advance (optional).  If not, the simulatoin
    *                            advances with no set stop time
    */
  case class StartResume(
      MessageKind: String,
      ReceivingEntity: String,
      RequestIdentifier: Int,
      RealWorldTime: Long,
      SimulationStartTime: Long,
      AdvanceTime: Option[Long]
  ) extends Command

  /** Message sent by time constrained time stepping clients to advance to their next time step.
    * @param MessageKind Must be "TimeAdvanceRequest"
    * @param client the client sending the request
    * @param NextLogicalTime the time to which the client would like to advance
    */
  case class TimeAdvanceRequest(
      MessageKind: String,
      client: String,
      NextLogicalTime: Long
  ) extends Command

  /** Message sent by time constrained event stepping clients to advance to the next event
    * @param MessageKind must be "NextEventRequest"
    * @param client the client sending the request
    * @param NextLogicalTime the logical time of the next internal event for the requesting client
    */
  case class NextEventRequest(
      MessageKind: String,
      client: String,
      NextLogicalTime: Long
  ) extends Command

  /** Message sent back to time constrained clients to notify them to advance to the time in this message.  This message
    * is sent once WebLVC can be sure that no messages from the past will be sent to the client.  This occurs when the
    * client's lower bound time constraint is greater than or equal to the next logical time in this message.
    * @param MessageKind must be "TimeAdvanceGranted"
    * @param NextLogicalTime the time to which the client is allowed to advance.  For time stepping clients, this will
    *                        be the time of the corresponding TimeAdvanceRequest.  For event stepping clients, this
    *                        will be the time of the next event, whether that event is internal to the client
    *                        or externally sent by WebLVC.
    */
  case class TimeAdvanceGranted(MessageKind: String, NextLogicalTime: Long)

  case class TimeManagedStateUpdate(
      stateUpdate: StateManagerActor.AttributeUpdate
  ) extends Command

  case class TimeManagedInteraction(
      interaction: InteractionManagerActor.Interaction
  ) extends Command

  case class StateUpdateComplete(uuid: String) extends Command

  case class InteractionComplete(uuid: String) extends Command

  case class PendingMessages(
      stateUpdateIds: List[String],
      interactionIds: List[String]
  ) {
    def isEmpty: Boolean = {
      stateUpdateIds.isEmpty && interactionIds.isEmpty
    }
  }

  /** Message sent to stop execution of the simulation.  This message will cause WebLVC to stop granting time
    * advances until it receives a subsequent StartResume message
    * @param MessageKind must be "StopFreeze"
    * @param ReceivingEntity the recipient of the message
    */
  case class StopFreeze(MessageKind: String, ReceivingEntity: String)
      extends Command

  /** This stores the time management data for a client.  A regulating federate will publish a lookahead value
    * A constrained federate will have a lower bound time constraint
    *
    * @param lowerBoundTimeConstraint The earliest possible message that might be generated by other regulating
    *                                 federates.  A constrained federate cannot advance beyond this time (epoch milliseconds)
    * @param lookAhead                the interval, beyond logical time, at which a client could publish its earliest
    *                                 next event (milliseconds)
    * @param eventStepped             If true, the client is event stepped and will advance via NextEventRequest messages
    *                                 Otherwise, the client is time stepped and will advance via TimeAdvanceRequest messages
    * @param clientLogicalTime        the logical time of the client (epoch milliseconds)
    * @param pendingAdvance           the time to which the client has requested an advance
    * @param receiveOrderQueue        A list of non-timestamped messages waiting to be sent to the client at the next time advance
    * @param timeOrderQueue           A list of time ordered messages that will be sent to the client once the lowerBoundTimeConstraint
    *                                 for the client has been reached
    */
  case class ClientData(
      lowerBoundTimeConstraint: Option[Long],
      lookAhead: Option[Long],
      eventStepped: Boolean,
      clientLogicalTime: Long,
      pendingAdvance: Option[Long],
      receiveOrderQueue: List[AddressedMessage],
      timeOrderQueue: TreeMap[Long, List[AddressedMessage]]
  )
}
