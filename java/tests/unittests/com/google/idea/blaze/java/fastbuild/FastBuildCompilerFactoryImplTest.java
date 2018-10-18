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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaToolchainInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildCompiler.CompileInstructions;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FastBuildCompilerFactoryImpl}. */
@RunWith(JUnit4.class)
public final class FastBuildCompilerFactoryImplTest {

  private static final File JAVAC_JAR = new File(System.getProperty("javac.jar"));
  private static final File GUAVA_JAR = new File(System.getProperty("guava.jar"));
  private static final File TRUTH_JAR = new File(System.getProperty("truth.jar"));
  private static final String WORKSPACE_NAME = "io_bazel";

  private static final JavaToolchainInfo JAVA_TOOLCHAIN =
      JavaToolchainInfo.create(
          ArtifactLocation.builder().setRelativePath(JAVAC_JAR.getPath()).build(),
          /* sourceVersion */ "8",
          /* targetVersion */ "8");
  private static final JavaInfo JAVA_LIBRARY_WITHOUT_SOURCES =
      JavaInfo.create(
          /*sources*/ ImmutableSet.of(),
          /*testClass*/ null,
          /* annotationProcessorClassNames */ ImmutableList.of(),
          /* annotationProcessorClassPath */ ImmutableList.of(),
          /* jvmFlags */ ImmutableList.of());

  @Test
  public void testNoJavaToolchain() {
    Map<Label, FastBuildBlazeData> blazeData = new HashMap<>();
    Label targetLabel = Label.create("//our/build:target");
    Label dependencyLabel = Label.create("//some/package:javalibs");
    FastBuildBlazeData targetData =
        FastBuildBlazeData.builder()
            .setLabel(targetLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setDependencies(ImmutableSet.of(dependencyLabel))
            .setJavaInfo(JAVA_LIBRARY_WITHOUT_SOURCES)
            .build();
    FastBuildBlazeData dependencyData =
        FastBuildBlazeData.builder()
            .setLabel(dependencyLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setJavaInfo(JAVA_LIBRARY_WITHOUT_SOURCES)
            .build();
    blazeData.put(targetLabel, targetData);
    blazeData.put(dependencyLabel, dependencyData);

    try {
      createCompilerFactory().getCompilerFor(targetLabel, blazeData);
      fail("Should have thrown FastBuildException");
    } catch (FastBuildException e) {
      assertThat(e.getMessage()).contains("Java toolchain");
    }
  }

  @Test
  public void testMultipleJavaToolchains() {
    Map<Label, FastBuildBlazeData> blazeData = new HashMap<>();
    Label targetLabel = Label.create("//our/build:target");
    Label jdkOneLabel = Label.create("//some/jdk:langtools");
    Label jdkTwoLabel = Label.create("//other/jdk:langtools");
    FastBuildBlazeData targetData =
        FastBuildBlazeData.builder()
            .setLabel(targetLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setDependencies(ImmutableSet.of(jdkOneLabel, jdkTwoLabel))
            .setJavaInfo(JAVA_LIBRARY_WITHOUT_SOURCES)
            .build();
    FastBuildBlazeData jdkOneData =
        FastBuildBlazeData.builder()
            .setLabel(jdkOneLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setJavaInfo(JAVA_LIBRARY_WITHOUT_SOURCES)
            .build();
    FastBuildBlazeData jdkTwoData =
        FastBuildBlazeData.builder()
            .setLabel(jdkTwoLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setJavaInfo(JAVA_LIBRARY_WITHOUT_SOURCES)
            .build();
    blazeData.put(targetLabel, targetData);
    blazeData.put(jdkOneLabel, jdkOneData);
    blazeData.put(jdkTwoLabel, jdkTwoData);

    try {
      createCompilerFactory().getCompilerFor(targetLabel, blazeData);
      fail("Should have thrown FastBuildException");
    } catch (FastBuildException e) {
      assertThat(e.getMessage()).contains("Java toolchain");
    }
  }

  @Test
  public void testSimpleCompile() throws IOException, FastBuildException {
    String java =
        ""
            + "package com.google.idea.blaze.java.fastbuild;\n"
            + "\n"
            + "final class Main {\n"
            + "  private static void main(String[] args) {\n"
            + "    System.out.println(\"success\");\n"
            + "  }\n"
            + "}\n";
    StringWriter javacOutput = new StringWriter();
    try {
      compile(java, javacOutput);
    } catch (FastBuildIncrementalCompileException e) {
      throw new AssertionError("Compilation failed:\n" + javacOutput, e);
    }
  }

  @Test
  public void testFindsClassesInClasspathJars() throws IOException, FastBuildException {
    String java =
        ""
            + "package com.google.idea.blaze.java.fastbuild;\n"
            + "\n"
            + "import com.google.common.collect.ImmutableSet;\n"
            + "import com.google.common.truth.Truth;\n"
            + "\n"
            + "final class Main {\n"
            + "  private static void main(String[] args) {\n"
            + "    System.out.println(\"success\");\n"
            + "  }\n"
            + "}\n";
    StringWriter javacOutput = new StringWriter();
    try {
      compile(java, javacOutput, GUAVA_JAR, TRUTH_JAR);
    } catch (FastBuildIncrementalCompileException e) {
      throw new AssertionError("Compilation failed:\n" + javacOutput, e);
    }
  }

  @Test
  public void errorOnMissingClasses() throws IOException, FastBuildException {
    String java =
        ""
            + "package com.google.idea.blaze.java.fastbuild;\n"
            + "\n"
            + "import com.google.common.collect.ImmutableSet;\n"
            + "import com.google.common.truth.Truth;\n"
            + "\n"
            + "final class Main {\n"
            + "  private static void main(String[] args) {\n"
            + "    System.out.println(\"success\");\n"
            + "  }\n"
            + "}\n";
    StringWriter javacOutput = new StringWriter();
    try {
      compile(java, javacOutput);
      fail("Should have thrown FastBuildIncrementalCompileException");
    } catch (FastBuildIncrementalCompileException e) {
      assertThat(javacOutput.toString()).contains("ImmutableSet");
      assertThat(javacOutput.toString()).contains("Truth");
    }
  }

  @Test
  public void errorOnTooNewSource() throws IOException, FastBuildException {
    String java =
        ""
            + "package com.google.idea.blaze.java.fastbuild;\n"
            + "\n"
            + "import java.util.ArrayList;\n"
            + "\n"
            + "final class Main {\n"
            + "  private static void main(String[] args) {\n"
            + "    Runnable r = () -> {};\n"
            + "  }\n"
            + "}\n";
    StringWriter javacOutput = new StringWriter();
    try {
      getCompiler(
              JavaToolchainInfo.create(
                  ArtifactLocation.builder().setRelativePath(JAVAC_JAR.getPath()).build(),
                  /* sourceVersion */ "7",
                  /* targetVersion */ "8"))
          .compile(createCompileInstructions(java, javacOutput).build(), new HashMap<>());
      fail("Should have thrown FastBuildIncrementalCompileException");
    } catch (FastBuildIncrementalCompileException e) {
      assertThat(javacOutput.toString()).contains("lambda");
      assertThat(javacOutput.toString()).contains("-source");
    }
  }

  private void compile(String source, Writer javacOutput, File... classpath)
      throws IOException, FastBuildException {
    getCompiler()
        .compile(
            createCompileInstructions(source, javacOutput, classpath).build(), new HashMap<>());
  }

  private CompileInstructions.Builder createCompileInstructions(
      String source, Writer javacOutput, File... classpath) throws IOException {
    Path outputDirectory = createOutputDirectory();
    Path javaFile = createJavaFile(source);
    return CompileInstructions.builder()
        .outputDirectory(outputDirectory.toFile())
        .filesToCompile(ImmutableList.of(javaFile.toFile()))
        .classpath(ImmutableList.copyOf(classpath))
        .outputWriter(javacOutput);
  }

  private Path createJavaFile(String java) throws IOException {
    Path javaFile = Files.createTempFile("CompileTest", ".java");
    javaFile.toFile().deleteOnExit();
    Files.write(javaFile, java.getBytes(StandardCharsets.UTF_8));
    return javaFile;
  }

  private Path createOutputDirectory() throws IOException {
    Path outputDirectory = Files.createTempDirectory("compile-test");
    outputDirectory.toFile().deleteOnExit();
    return outputDirectory;
  }

  public FastBuildCompiler getCompiler() throws FastBuildException {
    return getCompiler(JAVA_TOOLCHAIN);
  }

  private FastBuildCompiler getCompiler(JavaToolchainInfo javaToolchain) throws FastBuildException {
    Map<Label, FastBuildBlazeData> blazeData = new HashMap<>();
    Label targetLabel = Label.create("//our/build:target");
    Label jdkLabel = Label.create("//some/jdk:langtools");
    FastBuildBlazeData targetData =
        FastBuildBlazeData.builder()
            .setLabel(targetLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setDependencies(ImmutableSet.of(jdkLabel))
            .setJavaInfo(JAVA_LIBRARY_WITHOUT_SOURCES)
            .build();
    FastBuildBlazeData jdkData =
        FastBuildBlazeData.builder()
            .setLabel(jdkLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setJavaToolchainInfo(javaToolchain)
            .build();
    blazeData.put(targetLabel, targetData);
    blazeData.put(jdkLabel, jdkData);

    return createCompilerFactory().getCompilerFor(targetLabel, blazeData);
  }

  private static FastBuildCompilerFactoryImpl createCompilerFactory() {
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder()
            .setArtifactLocationDecoder(artifact -> new File(artifact.getRelativePath()))
            .build();
    BlazeProjectDataManager projectDataManager = new MockBlazeProjectDataManager(projectData);
    return new FastBuildCompilerFactoryImpl(projectDataManager);
  }
}
