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
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaToolchainInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildCompiler.CompileInstructions;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileManagerListener;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FastBuildCompilerFactoryImpl}. */
@RunWith(JUnit4.class)
public final class FastBuildCompilerFactoryImplTest extends BlazeTestCase {

  private static final String AUTO_VALUE_PROCESSOR =
      "com.google.auto.value.processor.AutoValueProcessor";
  private static final File AUTO_VALUE_JAR = new File(System.getProperty("auto_value.jar"));
  private static final File AUTO_VALUE_PLUGIN_JAR =
      new File(System.getProperty("auto_value_plugin.jar"));
  private static final File FAST_BUILD_JAVAC_JAR =
      new File(System.getProperty("fast_build_javac.jar"));
  private static final File GUAVA_JAR = new File(System.getProperty("guava.jar"));
  private static final File TRUTH_JAR = new File(System.getProperty("truth.jar"));
  private static final String WORKSPACE_NAME = "io_bazel";

  private static final JavaToolchainInfo JAVA_TOOLCHAIN =
      JavaToolchainInfo.create(
          /* javacJars= */ ImmutableList.of(),
          /* bootJars= */ ImmutableList.of(),
          /* sourceVersion= */ "8",
          /* targetVersion= */ "8");
  private static final JavaInfo JAVA_LIBRARY_WITHOUT_SOURCES = JavaInfo.builder().build();

  private FastBuildCompilerFactory compilerFactory;

  @BeforeClass
  public static void verifyJars() {
    checkState(AUTO_VALUE_JAR.exists());
    checkState(AUTO_VALUE_PLUGIN_JAR.exists());
    checkState(GUAVA_JAR.exists());
    checkState(FAST_BUILD_JAVAC_JAR.exists());
    checkState(TRUTH_JAR.exists());
  }

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder()
            .setArtifactLocationDecoder(new MockArtifactLocationDecoder())
            .build();
    BlazeProjectDataManager projectDataManager = new MockBlazeProjectDataManager(projectData);
    compilerFactory =
        FastBuildCompilerFactoryImpl.createForTest(projectDataManager, FAST_BUILD_JAVAC_JAR);

    registerExtensionPointByName("com.intellij.virtualFileManagerListener", VirtualFileManagerListener.class);
    applicationServices.register(VirtualFileManager.class, new VirtualFileManagerImpl(List.of(new CoreLocalFileSystem())));
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
      assertThat(e.getMessage()).contains("Couldn't find a Java toolchain");
    }
  }

  @Test
  public void testMultipleDifferentJavaToolchains() {
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
            .setJavaToolchainInfo(JAVA_TOOLCHAIN)
            .build();
    FastBuildBlazeData jdkTwoData =
        FastBuildBlazeData.builder()
            .setLabel(jdkTwoLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setJavaInfo(JAVA_LIBRARY_WITHOUT_SOURCES)
            .setJavaToolchainInfo(
                JavaToolchainInfo.create(
                    /* javacJars= */ ImmutableList.of(),
                    /* bootJars= */ ImmutableList.of(),
                    /* sourceVersion= */ "12345",
                    /* targetVersion= */ "9876"))
            .build();
    blazeData.put(targetLabel, targetData);
    blazeData.put(jdkOneLabel, jdkOneData);
    blazeData.put(jdkTwoLabel, jdkTwoData);

    try {
      compilerFactory.getCompilerFor(targetLabel, blazeData);
      fail("Should have thrown FastBuildException");
    } catch (FastBuildException e) {
      assertThat(e.getMessage()).contains("Found multiple Java toolchains");
    }
  }

  @Test
  public void testMultipleIdenticalJavaToolchains() throws FastBuildException {
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
            .setJavaToolchainInfo(JAVA_TOOLCHAIN)
            .build();
    FastBuildBlazeData jdkTwoData =
        FastBuildBlazeData.builder()
            .setLabel(jdkTwoLabel)
            .setWorkspaceName(WORKSPACE_NAME)
            .setJavaInfo(JAVA_LIBRARY_WITHOUT_SOURCES)
            .setJavaToolchainInfo(JAVA_TOOLCHAIN)
            .build();
    blazeData.put(targetLabel, targetData);
    blazeData.put(jdkOneLabel, jdkOneData);
    blazeData.put(jdkTwoLabel, jdkTwoData);

    // If this doesn't throw, the test passes.
    compilerFactory.getCompilerFor(targetLabel, blazeData);
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
            + "    var x = 100;\n"
            + "  }\n"
            + "}\n";
    StringWriter javacOutput = new StringWriter();
    try {
      getCompiler(
              JavaToolchainInfo.create(
                  /* javacJars= */ ImmutableList.of(),
                  /* bootJars= */ ImmutableList.of(),
                  /* sourceVersion= */ "8",
                  /* targetVersion= */ "8"))
          .compile(createBlazeContext(javacOutput), createCompileInstructions(java).build());
      fail("Should have thrown FastBuildIncrementalCompileException");
    } catch (FastBuildIncrementalCompileException e) {
      // Compiler error expected on line 7, because var is not supported in java 8
      assertThat(javacOutput.toString()).contains("7: error: cannot find symbol");
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
          createCompileInstructions(java, AUTO_VALUE_JAR)
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
            createBlazeContext(javacOutput), createCompileInstructions(source, classpath).build());
  }

  private CompileInstructions.Builder createCompileInstructions(String source, File... classpath)
      throws IOException {
    Path outputDirectory = createOutputDirectory();
    Path javaFile = createJavaFile(source);
    return CompileInstructions.builder()
        .outputDirectory(outputDirectory.toFile())
        .filesToCompile(ImmutableList.of(javaFile.toFile()))
        .classpath(ImmutableList.copyOf(classpath));
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
    return BlazeContext.create()
        .addOutputSink(PrintOutput.class, new WritingOutputSink(javacOutput));
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
