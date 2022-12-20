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

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue}

object StateUpdateActor extends DefaultJsonProtocol with SprayJsonSupport {

  def getTimeoutMillisFromJsObject(jsObject: JsObject): Option[Long] = {
    jsObject.fields.get("Timeout") match {
      case Some(timeoutValue) =>
        try {
          val timeoutMillis = timeoutValue.convertTo[Long]
          if (timeoutMillis == 0) {
            None
          } else {
            Some(timeoutMillis)
          }

        } catch {
          case ex: Exception =>
            val logMessage =
              s"Could not convert timeout value $jsObject to seconds.  Discarding timeout"
            System.err.println(logMessage)
            None
        }
      case None =>
        None
    }
  }

  def apply(
      objectType: String,
      firstUpdate: StateUpdate,
      stateManager: ActorRef[StateManagerActor.Command],
      uuid: String
  ): Behavior[Command] = {
    Behaviors.setup[Command] { context =>
      val timeOutMillis = getTimeoutMillisFromJsObject(firstUpdate.update)
      val initialState: StateValue =
        StateValue(firstUpdate.timeInMillis, timeOutMillis, firstUpdate.update)
      val stateMap: Map[String, StateValue] =
        Map(firstUpdate.objectName -> initialState)
      context.log.debug(
        s"Setup complete for ${firstUpdate.objectName} of type $objectType"
      )
      stateManager ! StateManagerActor.ChangesReply(
        firstUpdate.client,
        objectType,
        firstUpdate.update,
        Some(firstUpdate.update),
        uuid
      )
      stateUpdateBehavior(objectType, stateMap, stateManager)
    }
  }

  private def stateUpdateBehavior(
      objectType: String,
      stateMap: Map[String, StateValue],
      stateManager: ActorRef[StateManagerActor.Command]
  ): Behavior[Command] = {

    Behaviors.receive { (context, message) =>
      message match {
        case StateUpdate(client, timeInMillis, objectName, update, uuid) =>
          context.log.debug(
            s"Received state update at $timeInMillis, ${update.prettyPrint}"
          )
          stateMap.get(objectName) match {
            case Some(currentState) =>
              if (timeInMillis > currentState.latestUpdateMillis) {
                val (
                  updatedFullState: JsObject,
                  changesOnly: Option[JsObject]
                ) = update.fields.get("Object") match {
                  case Some(o) =>
                    currentState.getChanges(o.asJsObject) match {
                      case Some(changes) =>
                        val fullChangeObject =
                          JsObject(o.asJsObject.fields ++ changes.fields)
                        (
                          JsObject(
                            update.fields ++ Map("Object" -> fullChangeObject)
                          ),
                          Some(
                            JsObject(update.fields ++ Map("Object" -> changes))
                          )
                        )
                      case None =>
                        (update, None)
                    }
                  case None =>
                    (update, None)
                }
                if (changesOnly.isEmpty) {
                  context.log.debug(s"No changes for update")
                } else {
                  context.log.debug(s"Sending changes: ${changesOnly.get}")
                }
                stateManager ! StateManagerActor.ChangesReply(
                  client,
                  objectType,
                  updatedFullState,
                  changesOnly,
                  uuid
                )
                val timeoutMillisOption = getTimeoutMillisFromJsObject(update)
                stateUpdateBehavior(
                  objectType,
                  stateMap + (objectName -> StateValue(
                    timeInMillis,
                    timeoutMillisOption,
                    updatedFullState.fields("Object").asJsObject
                  )),
                  stateManager
                )
              } else {
                context.log.warn(
                  s"Received update for $objectName of type $objectType with timestamp $timeInMillis that was later than latest update at ${currentState.latestUpdateMillis}"
                )
                stateManager ! StateManagerActor.ChangesReply(
                  client,
                  objectType,
                  update,
                  None,
                  uuid
                )
                Behaviors.same
              }
            case None =>
              context.log.debug(
                s"Adding new object for $objectName to state map"
              )
              stateManager ! StateManagerActor.ChangesReply(
                client,
                objectType,
                update,
                Some(update),
                uuid
              )
              val timeoutMillisOption = getTimeoutMillisFromJsObject(update)
              stateUpdateBehavior(
                objectType,
                stateMap + (objectName -> StateValue(
                  timeInMillis,
                  timeoutMillisOption,
                  update.fields("Object").asJsObject
                )),
                stateManager
              )
          }
        case GetAllUpdates(client) =>
          val objectsToDeleteOptions: Iterable[Option[String]] = stateMap.map {
            entry =>
              val (objectName, stateValue) = entry
              val deleteOption: Option[String] =
                if (
                  stateValue.timeoutMillis.isEmpty || stateValue.latestUpdateMillis + stateValue.timeoutMillis.get > System
                    .currentTimeMillis()
                ) {
                  val requiredFields: Map[String, JsValue] = Map(
                    "MessageKind" -> JsString("AttributeUpdate"),
                    "Object" -> stateValue.latestState,
                    "ObjectName" -> JsString(objectName),
                    "ObjectType" -> JsString(objectType),
                    "Timestamp" -> JsObject(
                      "Format" ->
                        JsString("UnixEpochMilliseconds"),
                      "NumberValue" -> JsNumber(stateValue.latestUpdateMillis)
                    )
                  )
                  val update: JsObject = stateValue.timeoutMillis match {
                    case Some(timeout) =>
                      val updateFields: Map[String, JsValue] =
                        requiredFields + ("Timeout" -> JsNumber(timeout))
                      JsObject(updateFields)
                    case None =>
                      JsObject(requiredFields)
                  }
                  stateManager ! StateManagerActor.GetAllChangesReply(
                    client,
                    objectType,
                    update
                  )
                  None
                } else {
                  context.log.debug(
                    s"State value for $stateValue timed out.  Deleting"
                  )
                  Some(objectName)
                }
              deleteOption
          }
          val objectsToDelete = objectsToDeleteOptions.flatten
          val updatedStateMap = stateMap -- objectsToDelete
          stateUpdateBehavior(objectType, updatedStateMap, stateManager)
      }
    }
  }

  sealed trait Command

  case class StateUpdate(
      client: String,
      timeInMillis: Long,
      objectName: String,
      update: JsObject,
      uuid: String
  ) extends Command

  case class GetAllUpdates(client: String) extends Command

  case class ChangedState(
      updatedFullState: JsObject,
      changesOnly: Option[JsObject]
  )

  case class StateValue(
      latestUpdateMillis: Long,
      timeoutMillis: Option[Long],
      latestState: JsObject
  ) {
    def getChanges(update: JsObject): Option[JsObject] = {
      val changedFields: Map[String, JsValue] = update.fields.filter { entry =>
        val (key, value) = entry
        value.toString != latestState
          .fields("Object")
          .asJsObject
          .fields
          .getOrElse(key, JsString(""))
          .toString
      }
      if (changedFields.isEmpty) {
        None
      } else {
        Some(JsObject(changedFields))
      }

    }
  }

}
