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

import spray.json.JsObject

case class Subscription(subscriber: String, filter: JsObject) {
  def filter(stateValue: JsObject): Boolean = {
    val sameValues = filter.fields.map { filterEntry =>
      val (key, value) = filterEntry
      stateValue.fields
        .contains(key) && value.toString == stateValue.fields(key).toString
    }
    sameValues.toList.contains(false)
  }
}
