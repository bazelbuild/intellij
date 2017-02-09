/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.console;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;

/** ConsoleStream that decodes color codes before forwarding data to the next console stream. */
public class ColoredConsoleStream implements ColoredTextAcceptor, ConsoleStream {

  private final ConsoleStream consoleStream;
  private final AnsiEscapeDecoder ansiEscapeDecoder = new AnsiEscapeDecoder();

  public ColoredConsoleStream(ConsoleStream consoleStream) {
    this.consoleStream = consoleStream;
  }

  @Override
  public void print(String text, ConsoleViewContentType contentType) {
    Key<?> key =
        contentType == ConsoleViewContentType.ERROR_OUTPUT
            ? ProcessOutputTypes.STDERR
            : ProcessOutputTypes.STDOUT;
    ansiEscapeDecoder.escapeText(text, key, this);
  }

  @Override
  public void coloredTextAvailable(String escapedText, @SuppressWarnings("rawtypes") Key key) {
    ConsoleViewContentType contentType = ConsoleViewContentType.getConsoleViewType(key);
    consoleStream.print(escapedText, contentType);
  }
}
