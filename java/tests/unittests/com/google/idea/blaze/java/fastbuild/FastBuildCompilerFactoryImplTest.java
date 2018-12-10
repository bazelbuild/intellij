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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.PrintOutput;
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Unit tests for {@link FastBuildCompilerFactoryImpl}. */
@RunWith(Parameterized.class)
public final class FastBuildCompilerFactoryImplTest {

  private static final String AUTO_VALUE_PROCESSOR =
      "com.google.auto.value.processor.AutoValueProcessor";
  private static final File AUTO_VALUE_JAR = new File(System.getProperty("auto_value.jar"));
  private static final File AUTO_VALUE_PLUGIN_JAR =
      new File(System.getProperty("auto_value_plugin.jar"));
  private static final File FAST_BUILD_JAVAC_JAR =
      new File(System.getProperty("fast_build_javac.jar"));
  private static final File GUAVA_JAR = new File(System.getProperty("guava.jar"));
  private static final File JDK_TOOLS_JAR = new File(System.getProperty("jdk_tools.jar"));
  private static final File TRUTH_JAR = new File(System.getProperty("truth.jar"));
  private static final String WORKSPACE_NAME = "io_bazel";

  private static final JavaToolchainInfo JAVA_TOOLCHAIN =
      JavaToolchainInfo.create(
          ArtifactLocation.builder().setRelativePath(JDK_TOOLS_JAR.getPath()).build(),
          /* sourceVersion */ "8",
          /* targetVersion */ "8");
  private static final JavaInfo JAVA_LIBRARY_WITHOUT_SOURCES =
      JavaInfo.create(
          /*sources*/ ImmutableSet.of(),
          /*testClass*/ null,
          /*testSize*/ null,
          /* annotationProcessorClassNames */ ImmutableList.of(),
          /* annotationProcessorClassPath */ ImmutableList.of(),
          /* jvmFlags */ ImmutableList.of());

  private FastBuildCompilerFactory compilerFactory;
  private final boolean useNewCompiler;

  public FastBuildCompilerFactoryImplTest(boolean useNewCompiler) {
    this.useNewCompiler = useNewCompiler;
  }

  @Parameters(name = "useNewCompiler: {0}")
  public static Iterable<Boolean[]> data() {
    return ImmutableSet.of(new Boolean[] {true}, new Boolean[] {false});
  }

  @BeforeClass
  public static void verifyJars() {
    checkState(AUTO_VALUE_JAR.exists());
    checkState(AUTO_VALUE_PLUGIN_JAR.exists());
    checkState(GUAVA_JAR.exists());
    checkState(FAST_BUILD_JAVAC_JAR.exists());
    checkState(JDK_TOOLS_JAR.exists());
    checkState(TRUTH_JAR.exists());
  }

  @Before
  public void setUp() {
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder()
            .setArtifactLocationDecoder(artifact -> new File(artifact.getRelativePath()))
            .build();
    BlazeProjectDataManager projectDataManager = new MockBlazeProjectDataManager(projectData);
    compilerFactory =
        FastBuildCompilerFactoryImpl.createForTest(
            projectDataManager, useNewCompiler, FAST_BUILD_JAVAC_JAR);
  }

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
      compilerFactory.getCompilerFor(targetLabel, blazeData);
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
      compilerFactory.getCompilerFor(targetLabel, blazeData);
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
                  ArtifactLocation.builder().setRelativePath(JDK_TOOLS_JAR.getPath()).build(),
                  /* sourceVersion */ "7",
                  /* targetVersion */ "8"))
          .compile(
              createBlazeContext(javacOutput),
              createCompileInstructions(java, javacOutput).build());
      fail("Should have thrown FastBuildIncrementalCompileException");
    } catch (FastBuildIncrementalCompileException e) {
      assertThat(javacOutput.toString()).contains("lambda");
      assertThat(javacOutput.toString()).contains("-source");
    }
  }

  @Test
  public void runsAnnotationProcessors() throws IOException, FastBuildException {
    String java =
        ""
            + "package com.google.idea.blaze.java.fastbuild;\n"
            + "\n"
            + "import com.google.auto.value.AutoValue;\n"
            + "\n"
            + "@AutoValue\n"
            + "abstract class TestClass {\n"
            + "  abstract String someString();\n"
            + "  TestClass create(String someString) {\n"
            + "    return new AutoValue_TestClass(someString);\n"
            + "  }\n"
            + "}\n";
    StringWriter javacOutput = new StringWriter();
    FastBuildCompiler compiler = getCompiler();
    try {
      compiler.compile(
          createBlazeContext(javacOutput),
          createCompileInstructions(java, javacOutput, AUTO_VALUE_JAR)
              .annotationProcessorClasspath(ImmutableSet.of(AUTO_VALUE_PLUGIN_JAR))
              .annotationProcessorClassNames(ImmutableSet.of(AUTO_VALUE_PROCESSOR))
              .build());
    } catch (FastBuildIncrementalCompileException e) {
      throw new AssertionError("Compilation failed:\n" + javacOutput, e);
    }
  }

  private void compile(String source, Writer javacOutput, File... classpath)
      throws IOException, FastBuildException {
    getCompiler()
        .compile(
            createBlazeContext(javacOutput),
            createCompileInstructions(source, javacOutput, classpath).build());
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

    return compilerFactory.getCompilerFor(targetLabel, blazeData);
  }

  private static BlazeContext createBlazeContext(Writer javacOutput) {
    return new BlazeContext().addOutputSink(PrintOutput.class, new WritingOutputSink(javacOutput));
  }

  private static class WritingOutputSink implements OutputSink<PrintOutput> {

    private final Writer writer;

    private WritingOutputSink(Writer writer) {
      this.writer = writer;
    }

    @Override
    public Propagation onOutput(PrintOutput output) {
      try {
        writer.write(output.getText());
        return Propagation.Continue;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
