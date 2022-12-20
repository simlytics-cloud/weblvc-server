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

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsContextPks12 {
  // Load and initialize keystore object
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystoreFile: InputStream =
    getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")

  // valkeystoreFile = new FileIntputStream(new File("src/main/resources/keystore.pkcs12"))
  val password: Array[Char] =
    "akka-https".toCharArray // Fetch password from a s4cure place
  ks.load(keystoreFile, password)

  // Initialize a key manager
  val keyManagerFactory: KeyManagerFactory =
    KeyManagerFactory.getInstance("SunX509") // PKI = public key infrastructure
  keyManagerFactory.init(ks, password)

  // Initializr a trust manager
  val trustManagerFactory: TrustManagerFactory =
    TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // Initialize an SSL context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(
    keyManagerFactory.getKeyManagers,
    trustManagerFactory.getTrustManagers,
    new SecureRandom()
  )

  // Return the https connection context
  val httpsConnectionContext: HttpsConnectionContext =
    ConnectionContext.httpsServer(sslContext)

}
