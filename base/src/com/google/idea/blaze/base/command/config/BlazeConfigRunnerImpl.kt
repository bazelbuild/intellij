/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.config

import com.google.common.collect.ImmutableMap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.buildview.BazelExecService
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.model.BlazeConfiguration
import com.google.idea.blaze.base.model.BlazeConfigurationData
import com.google.idea.blaze.base.scope.BlazeContext
import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

private val LOG = logger<BlazeConfigRunnerImpl>()

class BlazeConfigRunnerImpl : BlazeConfigRunner {

  private val gson = Gson()

  @Throws(BlazeConfigException::class)
  override fun runBlazeConfig(
    project: Project,
    invoker: BuildSystem.BuildInvoker,
    context: BlazeContext
  ): BlazeConfigurationData {
    return BlazeConfigurationData.create(parseJson(runBlazeConfigGetJson(project, context, invoker)))
  }

  @Throws(BlazeConfigException::class)
  override fun runBlazeConfigGetJson(
    project: Project,
    context: BlazeContext,
    invoker: BuildSystem.BuildInvoker
  ): String {
    try {
      val cmdBuilder = BlazeCommand
        .builder(invoker, BlazeCommandName.CONFIG)
        .addBlazeFlags("--dump_all", "--output=json")

      return BazelExecService.instance(project).exec(context, cmdBuilder)
    } catch (e: ExecutionException) {
      throw BlazeConfigException("Failed to execute Bazel config command", e)
    }
  }

  @Throws(BlazeConfigException::class)
  private fun parseJson(json: String): ImmutableMap<String, BlazeConfiguration> {
    return runCatching { gson.fromJsonTyped<List<ConfigurationJson>>(json) }
      .getOrElse { throw BlazeConfigException("could not process config JSON", it) }
      .mapNotNull { runCatching { toBlazeConfiguration(it) }.getOrLogException(LOG) }
      .fold(ImmutableMap.builder<String, BlazeConfiguration>()) { acc, it -> acc.put(it.id(), it) }
      .build()
  }
}

private data class ConfigurationJson(
  val skyKey: String?,
  val configHash: String?,
  val mnemonic: String?,
  val isExec: Boolean = false,
  val fragmentOptions: List<FragmentOptionsJson> = emptyList(),
)

private data class FragmentOptionsJson(
  val name: String = "",
  val options: Map<String, String> = emptyMap(),
)

/**
 * Refined helper method for type GSON parsing.
 */
private inline fun <reified T> Gson.fromJsonTyped(json: String): T {
  return fromJson(json, object : TypeToken<T>() {}.type)
}

@Throws(IllegalStateException::class)
private fun toBlazeConfiguration(json: ConfigurationJson): BlazeConfiguration {
  if (json.mnemonic.isNullOrBlank()) {
    throw IllegalStateException("Missing field 'mnemonic' in config JSON")
  }
  if (json.configHash.isNullOrBlank()) {
    throw IllegalStateException("Missing field 'configHash' in config JSON")
  }

  val coreOptions = json.fragmentOptions.firstOrNull { it.name.contains("CoreOptions") }?.options
    ?: throw IllegalStateException("Missing 'CoreOptions' fragment in config JSON")
  val cpu = coreOptions["cpu"] ?: throw IllegalStateException("Missing option 'cpu'")
  val mode = coreOptions["compilation_mode"] ?: throw IllegalStateException("Missing option 'compilation_mode'")
  val platformSuffix = coreOptions["platform_suffix"] ?: "null" // can be null

  val platformName = buildString {
    append(cpu)
    append("-").append(mode)

    if (platformSuffix.isNotBlank() && platformSuffix != "null") {
      append("-").append(platformSuffix)
    }
  }

  return BlazeConfiguration.builder()
    .setId(json.configHash)
    .setMnemonic(json.mnemonic)
    .setPlatformName(platformName)
    .setCpu(cpu)
    .setIsToolConfiguration(json.isExec)
    .build()
}
