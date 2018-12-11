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

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.Reflection;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.IssueOutput.Category;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaToolchainInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildCompiler.CompileInstructions;
import com.google.idea.blaze.java.fastbuild.FastBuildLogDataScope.FastBuildLogOutput;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

final class FastBuildCompilerFactoryImpl implements FastBuildCompilerFactory {

  private static final Logger logger = Logger.getInstance(FastBuildCompilerFactoryImpl.class);

  private static final String JAVAC_CLASS = "com.sun.tools.javac.Main";
  private static final String FAST_BUILD_JAVAC_CLASS =
      "com.google.idea.blaze.java.fastbuild.FastBuildJavacImpl";
  private static final Path FAST_BUILD_JAVAC_JAR = Paths.get("lib", "libfast_build_javac.jar");

  private static final BoolExperiment useNewCompilerExperiment =
      new BoolExperiment("use.new.fast.run.compiler", true);

  private final BlazeProjectDataManager projectDataManager;
  private final Supplier<Boolean> useNewCompiler;
  private final Supplier<File> fastBuildJavacJarSupplier;

  private FastBuildCompilerFactoryImpl(
      BlazeProjectDataManager projectDataManager,
      Supplier<Boolean> useNewCompiler,
      Supplier<File> fastBuildJavacJarSupplier) {
    this.projectDataManager = projectDataManager;
    this.useNewCompiler = useNewCompiler;
    this.fastBuildJavacJarSupplier = fastBuildJavacJarSupplier;
  }

  FastBuildCompilerFactoryImpl(BlazeProjectDataManager projectDataManager) {
    this(
        projectDataManager,
        useNewCompilerExperiment::getValue,
        FastBuildCompilerFactoryImpl::findFastBuildJavacJar);
  }

  static FastBuildCompilerFactoryImpl createForTest(
      BlazeProjectDataManager projectDataManager, boolean useNewCompiler, File fastBuildJavacJar) {
    return new FastBuildCompilerFactoryImpl(
        projectDataManager, () -> useNewCompiler, () -> fastBuildJavacJar);
  }

  @Override
  public FastBuildCompiler getCompilerFor(Label label, Map<Label, FastBuildBlazeData> blazeData)
      throws FastBuildException {

    JavaToolchainInfo javaToolchain = getJavaToolchain(label, blazeData);

    BlazeProjectData projectData = projectDataManager.getBlazeProjectData();
    checkState(projectData != null, "not a blaze project");
    File javacJar = projectData.getArtifactLocationDecoder().decode(javaToolchain.javacJar());
    Javac javac = useNewCompiler.get() ? createNewCompiler(javacJar) : createOldCompiler(javacJar);
    return new JavacRunner(javac, javaToolchain.sourceVersion(), javaToolchain.targetVersion());
  }

  private JavaToolchainInfo getJavaToolchain(Label label, Map<Label, FastBuildBlazeData> blazeData)
      throws FastBuildException {
    FastBuildBlazeData targetData = blazeData.get(label);
    List<JavaToolchainInfo> javaToolchains = new ArrayList<>();
    for (Label dependency : targetData.dependencies()) {
      FastBuildBlazeData depInfo = blazeData.get(dependency);
      if (depInfo != null && depInfo.javaToolchainInfo().isPresent()) {
        javaToolchains.add(depInfo.javaToolchainInfo().get());
      }
    }
    if (javaToolchains.isEmpty()) {
      throw new FastBuildException(
          "Couldn't find a Java toolchain for target " + targetData.label());
    }
    if (javaToolchains.size() > 1) {
      throw new FastBuildException(
          "Found multiple Java toolchains for target " + targetData.label());
    }

    return javaToolchains.get(0);
  }

  @FunctionalInterface
  private interface Javac {
    boolean compile(
        BlazeContext context,
        List<String> javacArgs,
        Collection<File> files,
        PrintWriter outputWriter)
        throws FastBuildException;
  }

  private Javac createOldCompiler(File javacJar) throws FastBuildException {
    try {
      Class<?> javacClass = loadJavacClass(JAVAC_CLASS, javacJar);
      Object javacObject = javacClass.getDeclaredConstructor().newInstance();
      Method compileMethod = javacClass.getMethod("compile", String[].class, PrintWriter.class);
      return (context, javacArgs, files, writer) -> {
        List<String> args =
            ImmutableList.<String>builder()
                .addAll(javacArgs)
                .addAll(files.stream().map(File::getPath).collect(toSet()))
                .build();
        try {
          Integer result =
              (Integer) compileMethod.invoke(javacObject, args.toArray(new String[0]), writer);
          return result == 0;
        } catch (ReflectiveOperationException e) {
          throw new FastBuildException(e);
        }
      };
    } catch (MalformedURLException | ReflectiveOperationException e) {
      throw new FastBuildException(e);
    }
  }

  private Javac createNewCompiler(File javacJar) throws FastBuildException {
    try {
      Class<?> javacClass =
          loadJavacClass(FAST_BUILD_JAVAC_CLASS, javacJar, fastBuildJavacJarSupplier.get());

      Constructor<?> createMethod = javacClass.getConstructor();
      Object javacInstance = createMethod.newInstance();

      FastBuildJavac javaCompiler =
          Reflection.newProxy(
              FastBuildJavac.class, new MatchingMethodInvocationHandler(javacClass, javacInstance));
      return (context, javacArgs, files, writer) ->
          javaCompiler.compile(javacArgs, files, new ProblemsViewDiagnosticListener(context));
    } catch (MalformedURLException | ReflectiveOperationException e) {
      throw new FastBuildException(e);
    }
  }

  private static File findFastBuildJavacJar() {
    IdeaPluginDescriptor blazePlugin =
        PluginManager.getPlugin(
            PluginManager.getPluginByClassName(FastBuildCompilerFactoryImpl.class.getName()));
    return Paths.get(blazePlugin.getPath().getAbsolutePath())
        .resolve(FAST_BUILD_JAVAC_JAR)
        .toFile();
  }

  private Class<?> loadJavacClass(String javaCompilerClass, File... jars)
      throws MalformedURLException, ClassNotFoundException {
    URL[] urls = new URL[jars.length];
    for (int i = 0; i < jars.length; ++i) {
      urls[i] = jars[i].toURI().toURL();
    }
    URLClassLoader urlClassLoader = new URLClassLoader(urls, /* parent */ null);
    return urlClassLoader.loadClass(javaCompilerClass);
  }

  private static class JavacRunner implements FastBuildCompiler {

    private final Javac javac;
    private final String sourceVersion;
    private final String targetVersion;

    private JavacRunner(Javac javac, String sourceVersion, String targetVersion) {
      this.javac = javac;
      this.sourceVersion = sourceVersion;
      this.targetVersion = targetVersion;
    }

    @Override
    public void compile(BlazeContext context, CompileInstructions instructions)
        throws FastBuildException {
      ImmutableList.Builder<String> argsBuilder =
          ImmutableList.<String>builder()
              .add("-d")
              .add(instructions.outputDirectory().getPath())
              .add("-source")
              .add(sourceVersion)
              .add("-target")
              .add(targetVersion)
              .add("-cp")
              .add(instructions.classpath().stream().map(File::getPath).collect(joining(":")))
              .add("-g");
      if (instructions.annotationProcessorClassNames().isEmpty()) {
        // Without this, it will find all the annotation processors in the classpath and run them.
        // We only want to run them if the BUILD file asked for it.
        argsBuilder.add("-proc:none");
      } else {
        argsBuilder
            .add("-processor")
            .add(String.join(",", instructions.annotationProcessorClassNames()));
        if (!instructions.annotationProcessorClasspath().isEmpty()) {
          argsBuilder
              .add("-processorpath")
              .add(
                  instructions.annotationProcessorClasspath().stream()
                      .map(File::getPath)
                      .collect(joining(":")));
        }
      }
      List<String> args = argsBuilder.build();
      writeCompilationStartedMessage(context, instructions);
      logger.info("Running javac with options: " + args);
      Stopwatch timer = Stopwatch.createStarted();
      boolean success =
          javac.compile(context, args, instructions.filesToCompile(), instructions.outputWriter());
      timer.stop();
      writeCompilationFinishedMessage(context, instructions, success, timer);
      if (!success) {
        throw new FastBuildIncrementalCompileException();
      }
    }
  }

  private static void writeCompilationStartedMessage(
      BlazeContext context, CompileInstructions instructions) {
    if (instructions.annotationProcessorClassNames().isEmpty()) {
      context.output(
          new StatusOutput(
              String.format(
                  "Running javac on files %s", getSourceFileNames(instructions.filesToCompile()))));
    } else {
      context.output(
          new StatusOutput(
              String.format(
                  "Running javac on %s with annotation processors %s",
                  getSourceFileNames(instructions.filesToCompile()),
                  getProcessorNames(instructions.annotationProcessorClassNames()))));
    }
  }

  private static void writeCompilationFinishedMessage(
      BlazeContext context, CompileInstructions instructions, boolean success, Stopwatch timer) {
    context.output(new StatusOutput(String.format("Compilation finished in %s", timer)));

    context.output(FastBuildLogOutput.keyValue("javac_success", Boolean.toString(success)));
    context.output(
        FastBuildLogOutput.keyValue(
            "javac_source_file_count", Integer.toString(instructions.filesToCompile().size())));
    context.output(
        FastBuildLogOutput.keyValue(
            "javac_source_files", instructions.filesToCompile().toString()));
    context.output(
        FastBuildLogOutput.keyValue(
            "javac_annotation_processor_count",
            Integer.toString(instructions.annotationProcessorClassNames().size())));
    context.output(
        FastBuildLogOutput.keyValue(
            "javac_annotation_processors",
            instructions.annotationProcessorClassNames().toString()));
    context.output(FastBuildLogOutput.milliseconds("javac_time_ms", timer));
  }

  private static List<String> getSourceFileNames(Collection<File> sourceFiles) {
    return sourceFiles.stream().map(File::getName).collect(toList());
  }

  private static List<String> getProcessorNames(Collection<String> classNames) {
    return classNames.stream()
        .map(
            className -> {
              int lastDot = className.lastIndexOf('.');
              if (lastDot > -1 && lastDot < className.length() - 1) {
                return className.substring(lastDot + 1);
              } else {
                return className;
              }
            })
        .collect(toList());
  }

  private static class MatchingMethodInvocationHandler implements InvocationHandler {

    private final Class<?> matchingClass;
    private final Object matchingInstance;

    private MatchingMethodInvocationHandler(Class<?> matchingClass, Object matchingInstance) {
      this.matchingClass = matchingClass;
      this.matchingInstance = matchingInstance;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
      // We have two different copies of matchingClass from different classloaders, so we can't use
      // method directory. We have to look up the copy in the other classloader.
      Method otherMethod = matchingClass.getMethod(method.getName(), method.getParameterTypes());
      return otherMethod.invoke(matchingInstance, objects);
    }
  }

  private static class ProblemsViewDiagnosticListener
      implements DiagnosticListener<JavaFileObject> {

    private final BlazeContext context;

    private ProblemsViewDiagnosticListener(BlazeContext context) {
      this.context = context;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {

      IssueOutput.Builder output =
          IssueOutput.issue(toCategory(diagnostic.getKind()), diagnostic.getMessage(null));
      if (diagnostic.getSource() != null) {
        output.inFile(new File(diagnostic.getSource().toUri()));
      }
      if (diagnostic.getPosition() != Diagnostic.NOPOS) {
        output
            .onLine((int) diagnostic.getLineNumber())
            .inColumn((int) diagnostic.getColumnNumber());
      }
      context.output(output.build());
      context.output(new PrintOutput(diagnostic.toString()));
    }

    private Category toCategory(Kind kind) {
      switch (kind) {
        case ERROR:
          return Category.ERROR;
        case MANDATORY_WARNING:
        case WARNING:
          return Category.WARNING;
        case NOTE:
          return Category.NOTE;
        case OTHER:
          return Category.INFORMATION;
      }
      throw new AssertionError("Unknown Kind " + kind);
    }
  }
}
