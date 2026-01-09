/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
import com.google.common.io.BaseEncoding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.BufferedInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

// Usage: bazel run //tools/maintenance -- $PWD/MODULE.bazel && cp MODULE.bazel.out MODULE.bazel
fun main(args: Array<String>) {
  var content = Files.readString(Path.of(args[0]))

  content = bumpSdk("2025.3", eap = false, content)
  content = bumpPythonPlugin("253", content)
  content = bumpSdk("2025.2", eap = false, content)
  content = bumpPythonPlugin("252", content)

  Files.writeString(Paths.get("${args[0]}.out"), content,  StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
}

private fun bumpSdk(majorVersion: String, eap: Boolean, input: String): String {
  val version = getLatestSdkVersion(majorVersion, eap)
  val repository = if (eap) "snapshots" else "releases"
  val url = "https://www.jetbrains.com/intellij-repository/$repository/com/jetbrains/intellij/clion/clion/$version/clion-$version.zip"

  return bump("clion", majorVersion, version, getSha256(url), input)
}

private fun bumpPythonPlugin(majorVersion: String, input: String): String {
  val version = getLatestPluginVersion(7322, majorVersion)
  val url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/$version/PythonCore-$version.zip"

  return bump("PythonCore", majorVersion, version, getSha256(url), input)
}

private fun bump(name: String, majorVersion: String, version: String, sha256: String, input: String): String {
  return input
    .replace( // replace the version
      """(intellij_platform\.(sdk|plugin)[^)]*name = "$name"[^)]*version = ")($majorVersion[^"]*)""".toRegex()
    ) { result ->
      "${result.groupValues[1]}$version"
    }
    .replace( // replace the sha256
      """(intellij_platform\.(sdk|plugin)[^)]*name = "$name"[^)]*sha256 = ")([^"]+)("[^)]*version = "$version")""".toRegex()
    ) { result ->
      "${result.groupValues[1]}$sha256${result.groupValues[4]}"
    }
}

private fun getSha256(url: String): String {
  val icStream = BufferedInputStream(URL(url).openStream())
  val digest = MessageDigest.getInstance("SHA-256")
  var index = 0L // iterator's withIndex uses int instead of long
  icStream.iterator().forEachRemaining {
    index += 1
    digest.update(it)
    if (index % 10000000 == 0L) {
      println("${index / 1024 / 1024} mb of ${URL(url).file.split("/").last()} processed")
    }
  }
  val sha256sum = digest.digest(icStream.readAllBytes())
  return BaseEncoding.base16().encode(sha256sum).lowercase()
}

private fun getLatestSdkVersion(major: String, eap: Boolean): String {
  if (eap) {
    val version =
      URL("https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/clion/BUILD/$major-EAP-SNAPSHOT/BUILD-$major-EAP-SNAPSHOT.txt").readText()
    return "$version-EAP-SNAPSHOT"
  }

  val latestRelease =
    URL("https://data.services.jetbrains.com/products/releases?code=CL&type=release&majorVersion=$major&latest=true").readText()

  val json = Json.parseToJsonElement(latestRelease).jsonObject
  val clArray = json["CL"]?.jsonArray ?: error("CL array not found")
  return clArray[0].jsonObject["version"]?.jsonPrimitive?.content ?: error("Version not found")
}

fun getLatestPluginVersion(pluginId: Int, major: String): String {
  val text = URL("https://plugins.jetbrains.com/api/plugins/$pluginId/updates").readText()

  return Json.parseToJsonElement(text).jsonArray
    .mapNotNull { it.jsonObject["version"]?.jsonPrimitive?.content }
    .first { it.startsWith(major) }
}
