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

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.Diagnostic.Kind;

/**
 * A small wrapper around javac.
 *
 * <p>This interface is used by two classloaders (the plugin's and the compiler's) whose only shared
 * classes are those from the JRE, so it mustn't import anything besides that.
 *
 * <p>Additionally, the compiler and plugin can have different versions of java, so we try to limit
 * the interface to classes which don't change (javax.tools.DiagnosticListener is out), at the
 * expense of code cleanliness and type safety.
 */
// TODO(plumpy): have FastBuildJavacImpl implement JavaCompiler instead, which would get rid of the
// need for this class. It's a lot more complicated to do that, however.
interface FastBuildJavac {

  /** Returns an encoded version of CompilerOutput. Call {@link CompilerOutput#decode} to decode. */
  Object[] compile(List<String> args, Collection<File> sources);

  final class CompilerOutput {
    final boolean result;
    final List<DiagnosticLine> diagnostics;

    CompilerOutput(boolean result, List<DiagnosticLine> diagnostics) {
      this.result = result;
      this.diagnostics = diagnostics;
    }

    Object[] encode() {
      return new Object[] {
        result, diagnostics.stream().map(DiagnosticLine::encode).collect(Collectors.toList())
      };
    }

    @SuppressWarnings("unchecked")
    public static CompilerOutput decode(Object[] rawOutput) {
      boolean result = (boolean) rawOutput[0];
      List<DiagnosticLine> diagnostics =
          ((List<Object[]>) rawOutput[1])
              .stream().map(DiagnosticLine::decode).collect(Collectors.toList());
      return new CompilerOutput(result, diagnostics);
    }
  }

  final class DiagnosticLine {
    final Kind kind;
    final String message;
    final String formattedMessage;
    /** uri is nullable (but we can't add a dependency on jsr305 annotations) */
    final URI uri;

    final long lineNumber;
    final long columnNumber;

    DiagnosticLine(
        Kind kind,
        String message,
        String formattedMessage,
        URI uri,
        long lineNumber,
        long columnNumber) {
      this.kind = kind;
      this.message = message;
      this.formattedMessage = formattedMessage;
      this.uri = uri;
      this.lineNumber = lineNumber;
      this.columnNumber = columnNumber;
    }

    Object[] encode() {
      return new Object[] {
        kind.toString(),
        message,
        formattedMessage,
        uri == null ? null : uri.toString(),
        lineNumber,
        columnNumber
      };
    }

    @SuppressWarnings("unchecked")
    static DiagnosticLine decode(Object[] encoded) {
      return new DiagnosticLine(
          decodeKind((String) encoded[0]),
          (String) encoded[1],
          (String) encoded[2],
          decodeUri((String) encoded[3]),
          (long) encoded[4],
          (long) encoded[5]);
    }

    private static Kind decodeKind(String kind) {
      try {
        return Kind.valueOf(kind);
      } catch (IllegalArgumentException e) {
        return Kind.OTHER;
      }
    }

    private static URI decodeUri(String uri) {
      try {
        return uri == null ? null : URI.create(uri);
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
  }
}
