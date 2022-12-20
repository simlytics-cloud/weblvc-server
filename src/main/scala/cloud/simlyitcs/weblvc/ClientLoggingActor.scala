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
import spray.json.JsObject

import scala.collection.immutable.TreeMap

object ClientLoggingActor extends WebLvcJsonProtocol {
  def apply(
      client: String,
      mailManActor: ActorRef[MailManActor.Command],
      bufferSize: Int
  ): Behavior[Command] = {
    loggingBehavior(
      client,
      mailManActor,
      TreeMap[Long, LoggingStatus](
        1L -> LoggingStatus(java.time.Instant.now.toString, 1, "Started logger")
      ),
      bufferSize
    )
  }

  def loggingBehavior(
      client: String,
      mailManActor: ActorRef[MailManActor.Command],
      logs: TreeMap[Long, LoggingStatus],
      bufferSize: Int
  ): Behavior[Command] = {
    Behaviors.receive { (context, command) =>
      command match {
        case LogMessage(message) =>
          val loggingStatus = LoggingStatus(
            java.time.Instant.now().toString,
            logs.lastKey + 1,
            message
          )
          context.log.debug(s"$client log adding message $loggingStatus")
          val truncatedLogs = if (logs.size + 1 > bufferSize) {
            context.log.debug(
              s"Truncating $client log to buffer size: $bufferSize"
            )
            logs.takeRight(bufferSize - 1)
          } else {
            logs
          }
          loggingBehavior(
            client,
            mailManActor,
            truncatedLogs + (loggingStatus.Index -> loggingStatus),
            bufferSize
          )
        case StatusLogRequestWrapper(statusLogRequestJsObject) =>
          val statusLogRequest =
            statusLogRequestJsObject.convertTo[StatusLogRequest]
          val startIndex: Long = statusLogRequest.Offset match {
            case Some(x) if x == 0           => logs.firstKey
            case Some(y) if y > logs.lastKey => logs.lastKey
            case Some(z)                     => scala.math.max(z, logs.firstKey)
            case None                        => logs.firstKey
          }
          val endIndex: Long = statusLogRequest.Length match {
            case Some(x) if x == 0                           => logs.lastKey
            case Some(y) if logs.firstKey + y > logs.lastKey => logs.lastKey
            case Some(z)                                     => logs.firstKey + z
            case None                                        => logs.lastKey
          }
          val statusList: Array[LoggingStatus] = logs
            .slice(startIndex.toInt, endIndex.toInt)
            .values
            .map { log =>
              log.toJson.convertTo[LoggingStatus]
            }
            .toArray
          val statusLogResponse: StatusLogResponse =
            StatusLogResponse("StatusLogResponse", statusList)
          mailManActor ! MailManActor.AddressedMessage(
            client,
            statusLogResponse.toJson.asJsObject
          )
          Behaviors.same
      }
    }
  }

  sealed trait Command

  case class LogMessage(message: String) extends Command

  case class StatusLogRequestWrapper(statusLogRequest: JsObject) extends Command

  case class StatusLogRequest(
      MessageKind: String,
      Length: Option[Int],
      Offset: Option[Int]
  )

  case class StatusLogResponse(
      MessageKind: String,
      Status: Array[LoggingStatus]
  )

  case class LoggingStatus(Time: String, Index: Long, Message: String)

}
