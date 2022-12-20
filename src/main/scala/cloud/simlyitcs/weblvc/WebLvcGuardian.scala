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
import akka.actor.{ActorLogging, typed => typedActors}
import akka.event.Logging
import akka.{actor => classic}

object WebLvcGuardian {
  case class TypedActorsResponse(
      mailManActor: typedActors.ActorRef[MailManActor.Command],
      loggingActor: typedActors.ActorRef[LoggingActor.Command],
      stateManagerActor: typedActors.ActorRef[StateManagerActor.Command],
      interactionManagerActor: typedActors.ActorRef[
        InteractionManagerActor.Command
      ],
      timeManagerActor: typedActors.ActorRef[TimeManagerActor.Command]
  )

  case object GetTypedActors
}

class WebLvcGuardian extends classic.Actor {

  private val log = Logging(context.system, this)
  private val mailManActor: typedActors.ActorRef[MailManActor.Command] =
    context.spawn(MailManActor(), "mailMan")
  private val loggingActor: typedActors.ActorRef[LoggingActor.Command] =
    context.spawn(LoggingActor(1000), "loggingActor")
  private val stateManagerActor
      : typedActors.ActorRef[StateManagerActor.Command] =
    context.spawn(StateManagerActor(), "stateManagerActor")
  private val interactionManagerActor
      : typedActors.ActorRef[InteractionManagerActor.Command] =
    context.spawn(InteractionManagerActor(), "interactionManagerActor")
  private val timeManagerActor: typedActors.ActorRef[TimeManagerActor.Command] =
    context.spawn(TimeManagerActor(), "timeManagerActor")
  log.info("Starting guardian actor")

  def receive: Receive = { case WebLvcGuardian.GetTypedActors =>
    sender() ! WebLvcGuardian.TypedActorsResponse(
      mailManActor,
      loggingActor,
      stateManagerActor,
      interactionManagerActor,
      timeManagerActor
    )
  }
}
