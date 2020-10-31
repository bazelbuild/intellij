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
package com.google.idea.blaze.base.scope.output;

import com.google.idea.blaze.base.scope.Output;
import org.jetbrains.annotations.NotNull;

/** Output that can be printed to a log. */
public class PrintOutput implements Output {

  @NotNull private final String text;

  @NotNull private final OutputType outputType;

  /** The output type */
  public enum OutputType {
    NORMAL,
    LOGGED,
    ERROR
  }

  public PrintOutput(@NotNull String text, @NotNull OutputType outputType) {
    this.text = text;
    this.outputType = outputType;
  }

  public PrintOutput(@NotNull String text) {
    this(text, OutputType.NORMAL);
  }

  @NotNull
  public String getText() {
    return text;
  }

  @NotNull
  public OutputType getOutputType() {
    return outputType;
  }

  public static PrintOutput output(String text) {
    return new PrintOutput(text);
  }

  public static PrintOutput log(String text) {
    return new PrintOutput(text, OutputType.LOGGED);
  }

  public static PrintOutput error(String text) {
    return new PrintOutput(text, OutputType.ERROR);
  }
}
