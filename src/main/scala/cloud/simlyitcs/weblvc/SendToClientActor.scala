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
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.SourceQueueWithComplete
import spray.json.JsObject

/** This actor is instantiated with a queue source and protocol corresponding to a single WebLVC client.
  * When it receives a WebLVC message, it sends the message to the client via the queue using the
  * appropriate protocol
  */
object SendToClientActor {

  def apply(
      client: String,
      queue: SourceQueueWithComplete[Message],
      loggingActor: ActorRef[LoggingActor.Command]
  ): Behavior[JsObject] = {
    Behaviors.receive { (context, message) =>
      context.log.debug(s"Sending to $client: ${message.prettyPrint}")
      val outMessage: Option[Message] = Some(TextMessage(message.prettyPrint))
      if (outMessage.isDefined) queue.offer(outMessage.get)
      Behaviors.same
    }
  }
}
