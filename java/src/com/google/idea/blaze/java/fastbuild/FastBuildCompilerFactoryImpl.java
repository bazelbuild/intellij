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
import static com.google.common.collect.Iterables.transform;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaToolchainInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildCompiler.CompileInstructions;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class FastBuildCompilerFactoryImpl implements FastBuildCompilerFactory {

  private static final Logger logger = Logger.getInstance(FastBuildCompilerFactoryImpl.class);

  private static final String JAVAC_CLASS = "com.sun.tools.javac.Main";

  private final BlazeProjectDataManager projectDataManager;

  FastBuildCompilerFactoryImpl(BlazeProjectDataManager projectDataManager) {
    this.projectDataManager = projectDataManager;
  }

  @Override
  public FastBuildCompiler getCompilerFor(Label label, Map<Label, FastBuildBlazeData> blazeData)
      throws FastBuildException {

    JavaToolchainInfo javaToolchain = getJavaToolchain(label, blazeData);

    BlazeProjectData projectData = projectDataManager.getBlazeProjectData();
    checkState(projectData != null, "not a blaze project");
    return createCompiler(
        projectData.artifactLocationDecoder.decode(javaToolchain.javacJar()),
        javaToolchain.sourceVersion(),
        javaToolchain.targetVersion());
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

  private FastBuildCompiler createCompiler(
      File javacJar, String sourceVersion, String targetVersion) throws FastBuildException {
    try {
      URL url = javacJar.toURI().toURL();
      URLClassLoader urlClassLoader = new URLClassLoader(new URL[] {new URL("jar:" + url + "!/")});
      Class<?> javacClass = urlClassLoader.loadClass(JAVAC_CLASS);
      Object javacMain = javacClass.getDeclaredConstructor().newInstance();
      Method compileMethod = javacClass.getMethod("compile", String[].class, PrintWriter.class);
      return new ReflectiveJavac(javacMain, compileMethod, sourceVersion, targetVersion);
    } catch (MalformedURLException | ReflectiveOperationException e) {
      throw new FastBuildException(e);
    }
  }

  private static class ReflectiveJavac implements FastBuildCompiler {

    private final Object javacObject;
    private final Method compileMethod;
    private final String sourceVersion;
    private final String targetVersion;

    private ReflectiveJavac(
        Object javacObject, Method compileMethod, String sourceVersion, String targetVersion) {
      this.javacObject = javacObject;
      this.compileMethod = compileMethod;
      this.sourceVersion = sourceVersion;
      this.targetVersion = targetVersion;
    }

    @Override
    public void compile(CompileInstructions instructions, Map<String, String> loggingData)
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
            .add(instructions.annotationProcessorClassNames().stream().collect(joining(",")));
      }
      List<String> args =
          argsBuilder.addAll(transform(instructions.filesToCompile(), File::getPath)).build();
      writeCompilationStartedMessage(instructions);
      try {
        logger.info("Running javac with options: " + args);
        Stopwatch timer = Stopwatch.createStarted();
        Integer result =
            (Integer)
                compileMethod.invoke(
                    javacObject, args.toArray(new String[0]), instructions.outputWriter());
        timer.stop();
        writeCompilationFinishedMessage(instructions, result, timer, loggingData);
        if (result != 0) {
          throw new FastBuildCompileException("javac exited with code " + result, loggingData);
        }
      } catch (ReflectiveOperationException e) {
        throw new FastBuildException(e);
      }
    }
  }

  private static void writeCompilationStartedMessage(CompileInstructions instructions) {
    if (instructions.annotationProcessorClassNames().isEmpty()) {
      instructions
          .outputWriter()
          .printf("Running javac on files %s%n", getSourceFileNames(instructions.filesToCompile()));
    } else {
      instructions
          .outputWriter()
          .printf(
              "Running javac on %s with annotation processors %s%n",
              getSourceFileNames(instructions.filesToCompile()),
              getProcessorNames(instructions.annotationProcessorClassNames()));
    }
  }

  private static void writeCompilationFinishedMessage(
      CompileInstructions instructions,
      Integer result,
      Stopwatch timer,
      Map<String, String> loggingData) {
    instructions.outputWriter().printf("Compilation finished in %s%n", timer);
    loggingData.put("javac_success", Boolean.toString(result == 0));
    loggingData.put("javac_result", result.toString());
    loggingData.put(
        "javac_source_file_count", Integer.toString(instructions.filesToCompile().size()));
    loggingData.put("javac_source_files", instructions.filesToCompile().toString());
    loggingData.put(
        "javac_annotation_processor_count",
        Integer.toString(instructions.annotationProcessorClassNames().size()));
    loggingData.put(
        "javac_annotation_processors", instructions.annotationProcessorClassNames().toString());
    loggingData.put("javac_time_ms", Long.toString(timer.elapsed(TimeUnit.MILLISECONDS)));
  }

  private static List<String> getSourceFileNames(Collection<File> sourceFiles) {
    return sourceFiles.stream().map(File::getName).collect(toList());
  }

  private static List<String> getProcessorNames(Collection<String> classNames) {
    return classNames
        .stream()
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
}
