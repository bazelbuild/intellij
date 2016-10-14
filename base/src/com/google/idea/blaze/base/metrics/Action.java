/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.metrics;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * An item that can be logged. All actions contain a name that is used as a primary key. The name
 * should be immutable forever to keep the logs sane.
 *
 * <p>The name used by each {@link Action} should be [a-zA-Z0-9]* to keep things robust since
 * various log back ends may have different rules about what may or may not be in a key.
 *
 * <p>Do not use any of the following retired values for enums: INDEX_TOTAL_TIME("index")
 * REBUILD_TOTAL_TIME("rtt") SYNC_SAVE_FILES("ssf") SYNC_COMPUTE_MODULE_DIFF("scmd")
 * RUN_TOTAL_TIME("ttrp") DEBUG_TOTAL_TIME("ttsbp") RUN_TOTAL_TIME_FOR_ANDROID_TEST("ttrpat")
 * DEBUG_TOTAL_TIME_FOR_ANDROID_TEST("ttsbpat") IMPORT_TOTAL_TIME("tip")
 * IDE_BUILD_INFO_RESPONSE("ibi") RULES_EXTRACTION("re") BLAZE_MODULES_CREATION("mvc")
 * INTELLIJ_MODULE_CREATION("imc") SYNC_RESET_PROJECT("srp")
 *
 * <p>
 */
public enum Action {
  MAKE_PROJECT_TOTAL_TIME("mtt"),
  MAKE_MODULE_TOTAL_TIME("mmtt"),

  SYNC_TOTAL_TIME("stt"),
  SYNC_IMPORT_DATA_TIME("sidt"),
  BLAZE_BUILD_DURING_SYNC("bb"),
  BLAZE_BUILD("bld"),

  APK_BUILD_AND_INSTALL("apkbi"),

  BLAZE_COMMAND_USAGE("ttrpbc"),

  OPEN_IN_CODESEARCH("oics"),
  COPY_DEPOT_PATH("cg3p"),
  OPEN_CORRESPONDING_BUILD_FILE("ocbf"),

  CREATE_BLAZE_RULE("cbr"),
  CREATE_BLAZE_PACKAGE("cbp"),

  SYNC_SDK("ssdk"),

  C_RESOLVE_FILE("crf"),
  BLAZE_CLION_TEST_RUN("ctr"),
  BLAZE_CLION_TEST_DEBUG("ctd");

  @NotNull @NonNls private final String name;

  Action(@NotNull String name) {
    this.name = name;
  }

  @NotNull
  public String getName() {
    return name;
  }
}
