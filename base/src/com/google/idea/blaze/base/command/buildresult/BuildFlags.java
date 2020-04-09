/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.runtime.proto.CommandLineOuterClass;
import com.google.devtools.build.lib.runtime.proto.CommandLineOuterClass.CommandLineSection;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Predicate;

/** A data class representing blaze's build options for a build. */
public final class BuildFlags {
  private static final String STARTUP_OPTIONS_SECTION_LABEL = "startup options";
  private static final String CMDLINE_OPTIONS_SECTION_LABEL = "command options";

  static BuildFlags parseBep(
      InputStream bepStream,
      Predicate<String> startupFlagsFilter,
      Predicate<String> cmdlineFlagsFilter)
      throws BuildEventStreamException {
    return parseBep(
        BuildEventStreamProvider.fromInputStream(bepStream),
        startupFlagsFilter,
        cmdlineFlagsFilter);
  }

  public static BuildFlags parseBep(
      BuildEventStreamProvider stream,
      Predicate<String> startupFlagsFilter,
      Predicate<String> cmdlineFlagsFilter)
      throws BuildEventStreamException {
    BuildEventStreamProtos.BuildEvent event;
    ImmutableList.Builder<String> startupOptionsBuilder = ImmutableList.builder();
    ImmutableList.Builder<String> cmdlineOptionsBuilder = ImmutableList.builder();

    while ((event = stream.getNext()) != null) {
      switch (event.getId().getIdCase()) {
        case STRUCTURED_COMMAND_LINE:
          List<CommandLineSection> sections = event.getStructuredCommandLine().getSectionsList();
          for (CommandLineOuterClass.CommandLineSection commandLineSection : sections) {
            switch (commandLineSection.getSectionLabel()) {
              case STARTUP_OPTIONS_SECTION_LABEL:
                addOptionsToBuilder(
                    startupOptionsBuilder,
                    commandLineSection.getOptionList().getOptionList(),
                    startupFlagsFilter);
                continue;
              case CMDLINE_OPTIONS_SECTION_LABEL:
                addOptionsToBuilder(
                    cmdlineOptionsBuilder,
                    commandLineSection.getOptionList().getOptionList(),
                    cmdlineFlagsFilter);
                continue;
              default: // continue
            }
          }
          continue;
        default: // continue
      }
    }
    return new BuildFlags(startupOptionsBuilder.build(), cmdlineOptionsBuilder.build());
  }

  private static void addOptionsToBuilder(
      ImmutableList.Builder<String> builder,
      List<CommandLineOuterClass.Option> options,
      Predicate<String> flagsFilter) {
    for (CommandLineOuterClass.Option option : options) {
      String cmdlineOption = option.getCombinedForm().replace("'", "");
      if (!cmdlineOption.isEmpty() && flagsFilter.test(option.getOptionName())) {
        builder.add(cmdlineOption);
      }
    }
  }

  private final ImmutableList<String> startupOptions;
  private final ImmutableList<String> cmdlineOptions;

  public BuildFlags() {
    this(ImmutableList.of(), ImmutableList.of());
  }

  BuildFlags(ImmutableList<String> startupOptions, ImmutableList<String> cmdlineOptions) {
    this.startupOptions = startupOptions;
    this.cmdlineOptions = cmdlineOptions;
  }

  public ImmutableList<String> getStartupOptions() {
    return startupOptions;
  }

  public ImmutableList<String> getCmdlineOptions() {
    return cmdlineOptions;
  }
}
