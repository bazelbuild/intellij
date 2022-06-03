/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.scope.output.PrintOutput.OutputType;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Output class used to print task summary on consoles of top-level nodes. */
public class SummaryOutput implements Output {
  private final String text;

  private final OutputType outputType;

  /** A prefix that appears before the summary text message. */
  public enum Prefix {
    TIMESTAMP {
      @Override
      public String getDisplayText() {
        return LocalTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS).toString();
      }
    },
    INFO {
      @Override
      public String getDisplayText() {
        return "INFO:";
      }
    };

    abstract String getDisplayText();
  }

  private SummaryOutput(Prefix prefix, String text, OutputType outputType) {
    this.text = prefix.getDisplayText() + "\t" + text;
    this.outputType = outputType;
  }

  public String getText() {
    return text;
  }

  public OutputType getOutputType() {
    return outputType;
  }

  public PrintOutput toPrintOutput() {
    return new PrintOutput(this.text, this.outputType);
  }

  public static SummaryOutput output(Prefix prefix, String text) {
    return new SummaryOutput(prefix, text, OutputType.NORMAL);
  }

  public static SummaryOutput error(Prefix prefix, String text) {
    return new SummaryOutput(prefix, text, OutputType.ERROR);
  }
}
