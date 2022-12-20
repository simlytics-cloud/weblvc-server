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

import spray.json._

object Timestamp extends DefaultJsonProtocol {
  def getTimestampFromJsObject(jsObject: JsObject): Either[Long, String] = {
    jsObject.fields.get("Timestamp") match {
      case Some(timestampValue) =>
        timestampValue match {
          case jsObject: JsObject =>
            val fields = jsObject.fields
            if (fields.keys.toList.contains("Format")) {
              if (
                fields("Format")
                  .convertTo[String] == "UnixEpochMilliseconds"
              ) {
                fields.get("NumberValue") match {
                  case Some(numberValue) =>
                    try {
                      Left(numberValue.convertTo[Long])
                    } catch {
                      case e: Exception =>
                        Right(
                          s"Could not convert $numberValue to Long.  Discarding timestamp"
                        )
                    }
                  case None =>
                    Right(
                      s"${timestampValue.toJson} does not contain a NumberValue field.  Discarding timestamp"
                    )
                }
              } else {
                Right(
                  s"Illegal value for Timestamp format: " + fields("Format")
                    .convertTo[String]
                )
              }
            } else {
              val logMessage = s"Json object $jsObject has no Format fields"
              Right(logMessage)
            }
          case jsNumber: JsNumber =>
            try {
              Left(jsNumber.convertTo[Long])
            } catch {
              case ex: Exception =>
                val logMessage =
                  s"Could not convert timestamp value $jsObject to milliseconds.  Discarding timestamp"
                Right(logMessage)
            }
          case illegalValue =>
            Right(s"Illegal value for timestamp: " + timestampValue)

        }
      case None =>
        val logMessage = s"Json object $jsObject has no Timestamp"
        Right(logMessage)
    }
  }
}
