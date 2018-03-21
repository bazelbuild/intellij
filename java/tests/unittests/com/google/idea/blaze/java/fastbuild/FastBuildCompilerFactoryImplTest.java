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
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.fastbuild.FastBuildCompiler.CompileInstructions;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
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

  private FastBuildCompilerFactoryImpl compilerFactory;

  @Test
  public void testNoJavaToolchain() {
    Map<TargetKey, TargetIdeInfo> targetMap = new HashMap<>();
    TargetIdeInfo buildTargetInfo =
        TargetIdeInfo.builder()
            .setLabel(Label.create("//our/build:target"))
            .addDependency(Label.create("//some/package:javalibs"))
            .build();
    targetMap.put(TargetKey.forPlainTarget(Label.create("//our/build:target")), buildTargetInfo);
    targetMap.put(
        TargetKey.forPlainTarget(Label.create("//some/package:javalibs")),
        TargetIdeInfo.builder().build());
    configureTestForTargetMap(targetMap);

    try {
      compilerFactory.getCompilerFor(buildTargetInfo);
      fail("Should have thrown FastBuildException");
    } catch (FastBuildException e) {
      assertThat(e.getMessage()).contains("Java toolchain");
    }
  }

  @Test
  public void testMultipleJavaToolchains() {
    Map<TargetKey, TargetIdeInfo> targetMap = new HashMap<>();
    TargetIdeInfo buildTargetInfo =
        TargetIdeInfo.builder()
            .setLabel(Label.create("//our/build:target"))
            .addDependency(Label.create("//some/jdk:langtools"))
            .addDependency(Label.create("//other/jdk:langtools"))
            .build();
    targetMap.put(TargetKey.forPlainTarget(Label.create("//our/build:target")), buildTargetInfo);
    targetMap.put(
        TargetKey.forPlainTarget(Label.create("//some/jdk:langtools")),
        TargetIdeInfo.builder()
            .setJavaToolchainIdeInfo(
                JavaToolchainIdeInfo.builder()
                    .setJavacJar(
                        ArtifactLocation.builder().setRelativePath(JAVAC_JAR.getPath()).build()))
            .build());
    targetMap.put(
        TargetKey.forPlainTarget(Label.create("//other/jdk:langtools")),
        TargetIdeInfo.builder()
            .setJavaToolchainIdeInfo(
                JavaToolchainIdeInfo.builder()
                    .setJavacJar(
                        ArtifactLocation.builder().setRelativePath(JAVAC_JAR.getPath()).build()))
            .build());
    configureTestForTargetMap(targetMap);

    try {
      compilerFactory.getCompilerFor(buildTargetInfo);
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
    Path outputDirectory = createOutputDirectory();
    Path javaFile = createJavaFile(java);
    FastBuildCompiler compiler = getCompiler();
    StringWriter javacOutput = new StringWriter();
    try {
      compiler.compile(
          CompileInstructions.builder()
              .outputDirectory(outputDirectory.toFile())
              .filesToCompile(ImmutableList.of(javaFile.toFile()))
              .classpath(ImmutableList.of())
              .outputWriter(javacOutput)
              .build());
    } catch (FastBuildCompileException e) {
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
    Path outputDirectory = createOutputDirectory();
    Path javaFile = createJavaFile(java);
    FastBuildCompiler compiler = getCompiler();
    StringWriter javacOutput = new StringWriter();
    try {
      compiler.compile(
          CompileInstructions.builder()
              .outputDirectory(outputDirectory.toFile())
              .filesToCompile(ImmutableList.of(javaFile.toFile()))
              .classpath(ImmutableList.of(GUAVA_JAR, TRUTH_JAR))
              .outputWriter(javacOutput)
              .build());
    } catch (FastBuildCompileException e) {
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
    Path outputDirectory = createOutputDirectory();
    Path javaFile = createJavaFile(java);
    FastBuildCompiler compiler = getCompiler();
    StringWriter javacOutput = new StringWriter();
    try {
      compiler.compile(
          CompileInstructions.builder()
              .outputDirectory(outputDirectory.toFile())
              .filesToCompile(ImmutableList.of(javaFile.toFile()))
              .classpath(ImmutableList.of())
              .outputWriter(javacOutput)
              .build());
      fail("Should have thrown FastBuildCompileException");
    } catch (FastBuildCompileException e) {
      assertThat(javacOutput.toString()).contains("ImmutableSet");
      assertThat(javacOutput.toString()).contains("Truth");
    }
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
    Map<TargetKey, TargetIdeInfo> targetMap = new HashMap<>();
    TargetIdeInfo buildTargetInfo =
        TargetIdeInfo.builder()
            .setLabel(Label.create("//our/build:target"))
            .addDependency(Label.create("//third_party/java/jdk:langtools"))
            .build();
    targetMap.put(TargetKey.forPlainTarget(Label.create("//our/build:target")), buildTargetInfo);
    targetMap.put(
        TargetKey.forPlainTarget(Label.create("//third_party/java/jdk:langtools")),
        TargetIdeInfo.builder()
            .setJavaToolchainIdeInfo(
                JavaToolchainIdeInfo.builder()
                    .setJavacJar(
                        ArtifactLocation.builder().setRelativePath(JAVAC_JAR.getPath()).build()))
            .build());
    configureTestForTargetMap(targetMap);
    return compilerFactory.getCompilerFor(buildTargetInfo);
  }

  private void configureTestForTargetMap(Map<TargetKey, TargetIdeInfo> targetMap) {
    BlazeProjectData projectData =
        new BlazeProjectData(
            0,
            new TargetMap(ImmutableMap.copyOf(targetMap)),
            null,
            null,
            null,
            artifact -> new File(artifact.relativePath),
            null,
            null,
            null);
    BlazeProjectDataManager projectDataManager = new MockBlazeProjectDataManager(projectData);
    compilerFactory = new FastBuildCompilerFactoryImpl(projectDataManager);
  }
}
