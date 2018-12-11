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

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.ClientCodeWrapper.Trusted;
import com.sun.tools.javac.api.DiagnosticFormatter;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Log;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

/**
 * An implementation of {@link FastBuildJavac} that uses the OpenJDK compiler.
 *
 * <p>The point of this class is to create {@link FormattedJavacDiagnostic} objects. The OpenJDK
 * compiler will <em>either</em> write nicely formatted messages to the provided PrintWriter or call
 * the specified DiagnosticListener, but it won't do both. And the {@link Diagnostic} messages don't
 * contain the nicely formatted error messages that you expect from the compiler.
 *
 * <p>So this class creates a compiler and captures the {@code Diagnostic} objects, attaching the
 * formatted message from the compiler before sending them on. This is the same approach that Blaze
 * uses in BlazeJavacMain.
 */
public final class FastBuildJavacImpl implements FastBuildJavac {

  @Override
  public boolean compile(
      List<String> args,
      Collection<File> sources,
      DiagnosticListener<? super JavaFileObject> listener) {

    Context context = new Context();
    FormattingListener formattingListener = new FormattingListener(context, listener);
    JavacTool javacTool = JavacTool.create();
    JavacFileManager fileManager =
        javacTool.getStandardFileManager(
            formattingListener, Locale.ENGLISH, StandardCharsets.UTF_8);
    Iterable<? extends JavaFileObject> filesToCompile =
        fileManager.getJavaFileObjects(sources.toArray(new File[] {}));
    JavacTask task =
        javacTool.getTask(
            /* writer (ignored if a diagnosticListener is set) */ null,
            fileManager,
            formattingListener,
            args,
            /* classes */ null,
            filesToCompile,
            context);
    return task.call();
  }

  @Trusted
  private static final class FormattingListener
      implements javax.tools.DiagnosticListener<JavaFileObject> {

    private final Context context;
    private final DiagnosticListener<? super JavaFileObject> delegate;

    private FormattingListener(
        Context context, DiagnosticListener<? super JavaFileObject> delegate) {
      this.context = context;
      this.delegate = delegate;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      DiagnosticFormatter<JCDiagnostic> formatter = Log.instance(context).getDiagnosticFormatter();
      Locale locale = JavacMessages.instance(context).getCurrentLocale();
      String formatted = formatter.format((JCDiagnostic) diagnostic, locale);
      delegate.report(new FormattedJavacDiagnostic(diagnostic, formatted));
    }
  }
}
