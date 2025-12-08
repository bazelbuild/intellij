/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.command.BlazeCommand
import com.intellij.credentialStore.Credentials
import com.intellij.util.asSafely
import com.intellij.util.net.ProxyAuthentication
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import java.net.URLEncoder

private const val HTTP_PROXY_VARIABLE = "HTTP_PROXY"
private const val HTTPS_PROXY_VARIABLE = "HTTPS_PROXY"
private const val NO_PROXY_VARIABLE = "NO_PROXY"

object BazelProxyHelper {

  /**
   * Configures the proxy environment variables for Bazel to use. Called
   * automatically when using this service. Can be called manually if needed
   * in other places.
   */
  @JvmStatic
  fun getConfiguration(): Map<String, String> = getProxyConfiguration()
}

private fun buildProxyUrl(
  schema: String,
  configuration: ProxyConfiguration.StaticProxyConfiguration,
  credentials: Credentials?
): String {
  val builder = StringBuilder(schema).append("://")

  if (credentials != null) {
    builder
      .append(credentials.userName)
      .append(":")
      // Bazel only supports URL encoding for passwords, not the user names
      .append(URLEncoder.encode(credentials.getPasswordAsString(), "UTF-8"))
      .append("@")
  }

  builder
    .append(configuration.host)
    .append(":")
    .append(configuration.port)

  return builder.toString()
}

private fun getProxyConfiguration(): Map<String, String> {
  val configuration = ProxySettings.getInstance()
    .getProxyConfiguration()
    .asSafely<ProxyConfiguration.StaticProxyConfiguration>()
    ?: return emptyMap()

  if (configuration.protocol != ProxyConfiguration.ProxyProtocol.HTTP) {
    return emptyMap()
  }

  val authentication = ProxyAuthentication.getInstance()
    .getKnownAuthentication(configuration.host, configuration.port)

  // IntelliJ only supports http proxies
  val url = buildProxyUrl("http", configuration, authentication)

  return mapOf(
    HTTP_PROXY_VARIABLE to url,
    HTTPS_PROXY_VARIABLE to url,
    NO_PROXY_VARIABLE to configuration.exceptions,
  )
}

internal fun configureProxy(cmdBuilder: BlazeCommand.Builder) {
  cmdBuilder.addEnvironmentVariables(getProxyConfiguration())
}
