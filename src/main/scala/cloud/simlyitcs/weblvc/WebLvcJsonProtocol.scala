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

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait WebLvcJsonProtocol extends DefaultJsonProtocol {
  implicit val statusLogRequestFormat
      : RootJsonFormat[ClientLoggingActor.StatusLogRequest] = jsonFormat3(
    ClientLoggingActor.StatusLogRequest
  )
  implicit val loggingStatusFormat
      : RootJsonFormat[ClientLoggingActor.LoggingStatus] = jsonFormat3(
    ClientLoggingActor.LoggingStatus
  )
  implicit val statusLogResponseFormat
      : RootJsonFormat[ClientLoggingActor.StatusLogResponse] = jsonFormat2(
    ClientLoggingActor.StatusLogResponse
  )
  implicit val webLvcStartResumeFormat
      : RootJsonFormat[TimeManagerActor.StartResume] = jsonFormat6(
    TimeManagerActor.StartResume
  )
  implicit val webLvcStopFreezeFormat
      : RootJsonFormat[TimeManagerActor.StopFreeze] =
    jsonFormat2(TimeManagerActor.StopFreeze)

  implicit val timeAdvanceRequestFormat
      : RootJsonFormat[TimeManagerActor.TimeAdvanceRequest] = jsonFormat3(
    TimeManagerActor.TimeAdvanceRequest
  )
  implicit val nextEventRequestFormat
      : RootJsonFormat[TimeManagerActor.NextEventRequest] = jsonFormat3(
    TimeManagerActor.NextEventRequest
  )
  implicit val timeAdvanceGrantedFormat
      : RootJsonFormat[TimeManagerActor.TimeAdvanceGranted] = jsonFormat2(
    TimeManagerActor.TimeAdvanceGranted
  )

}
