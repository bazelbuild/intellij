/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.fastbuild;

import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/* A {@link Diagnostic} that retains the formatted message from the compiler. */
final class FormattedJavacDiagnostic implements Diagnostic<JavaFileObject> {

  public final Diagnostic<? extends JavaFileObject> diagnostic;
  public final String formatted;

  FormattedJavacDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic, String formatted) {
    this.diagnostic = diagnostic;
    this.formatted = formatted;
  }

  public String getFormatted() {
    return formatted;
  }

  @Override
  public String toString() {
    return formatted;
  }

  @Override
  public Kind getKind() {
    return diagnostic.getKind();
  }

  @Override
  public JavaFileObject getSource() {
    return diagnostic.getSource();
  }

  @Override
  public long getPosition() {
    return diagnostic.getPosition();
  }

  @Override
  public long getStartPosition() {
    return diagnostic.getStartPosition();
  }

  @Override
  public long getEndPosition() {
    return diagnostic.getEndPosition();
  }

  @Override
  public long getLineNumber() {
    return diagnostic.getLineNumber();
  }

  @Override
  public long getColumnNumber() {
    return diagnostic.getColumnNumber();
  }

  @Override
  public String getCode() {
    return diagnostic.getCode();
  }

  @Override
  public String getMessage(Locale locale) {
    return diagnostic.getMessage(locale);
  }
}
