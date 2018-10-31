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
package com.google.idea.blaze.android.targetmapbuilder;

import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbCcTarget.cc_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbCcToolchain.cc_toolchain;
import static com.google.idea.blaze.android.targetmapbuilder.NbJavaTarget.java_library;
import static com.google.idea.blaze.android.targetmapbuilder.TargetIdeInfoBuilderWrapper.targetMap;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.gson.GsonBuilder;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test new target map builders for correctness by comparing the target maps built with the new and
 * old builders.
 */
@RunWith(JUnit4.class)
public class NbTargetMapBuilderTest {
  @Test
  public void testCcTargetMap() throws Exception {
    TargetMap oldTargetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("android_ndk_linux/toolchains/BUILD"))
                    .setLabel("//android_ndk_linux/toolchains:armv7a")
                    .setKind(Kind.CC_TOOLCHAIN)
                    .setCToolchainInfo(
                        CToolchainIdeInfo.builder()
                            .setTargetName("arm-linux-androideabi")
                            .setCppExecutable(
                                new ExecutionRootPath("bin/arm-linux-androideabi-gcc"))
                            .addBaseCompilerOptions(
                                ImmutableList.of("-DOS_ANDROID", "-march=armv7-a"))
                            .addBuiltInIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath(
                                        "lib/gcc/arm-linux-androideabi/4.8/include")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("android_ndk_linux/toolchains/BUILD"))
                    .setLabel("//android_ndk_linux/toolchains:aarch64")
                    .setKind(Kind.CC_TOOLCHAIN)
                    .setCToolchainInfo(
                        CToolchainIdeInfo.builder()
                            .setTargetName("aarch64-linux-android")
                            .setCppExecutable(
                                new ExecutionRootPath("bin/aarch64-linux-android-gcc"))
                            .addBaseCompilerOptions(ImmutableList.of("-DOS_ANDROID"))
                            .addBuiltInIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath(
                                        "lib/gcc/aarch64-linux-android/4.9/include")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("java/com/google/BUILD"))
                    .setLabel("//java/com/google:native_lib2")
                    .setKind(Kind.CC_LIBRARY)
                    .setCInfo(
                        CIdeInfo.builder()
                            .addTransitiveQuoteIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("."),
                                    new ExecutionRootPath("blaze-out/android-aarch64/genfiles")))
                            .addTransitiveSystemIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("third_party/java/jdk/include")))
                            .addSource(source("java/com/google/jni/native2.cc")))
                    .addSource(source("java/com/google/jni/native2.cc"))
                    .addDependency("//android_ndk_linux/toolchains:aarch64"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("java/com/google/BUILD"))
                    .setLabel("//java/com/google:native_lib")
                    .setKind(Kind.CC_LIBRARY)
                    .setCInfo(
                        CIdeInfo.builder()
                            .addTransitiveQuoteIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("."),
                                    new ExecutionRootPath("blaze-out/android-armv7a/genfiles")))
                            .addTransitiveSystemIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("third_party/java/jdk/include")))
                            .addSource(source("java/com/google/jni/native.cc")))
                    .addSource(source("java/com/google/jni/native.cc"))
                    .addDependency("//java/com/google:native_lib2")
                    .addDependency("//android_ndk_linux/toolchains:armv7a"))
            .build();

    TargetMap newTargetMap =
        targetMap(
            cc_toolchain("//android_ndk_linux/toolchains:armv7a")
                .cc_target_name("arm-linux-androideabi")
                .cpp_executable("bin/arm-linux-androideabi-gcc")
                .base_compiler_options("-DOS_ANDROID", "-march=armv7-a")
                .built_in_include_dirs("lib/gcc/arm-linux-androideabi/4.8/include"),
            cc_toolchain("//android_ndk_linux/toolchains:aarch64")
                .cc_target_name("aarch64-linux-android")
                .cpp_executable("bin/aarch64-linux-android-gcc")
                .base_compiler_options("-DOS_ANDROID")
                .built_in_include_dirs("lib/gcc/aarch64-linux-android/4.9/include"),
            cc_library("//java/com/google:native_lib2")
                .transitive_quote_include_dirs(".", "blaze-out/android-aarch64/genfiles")
                .transitive_system_include_dirs("third_party/java/jdk/include")
                .src("jni/native2.cc")
                .dep("//android_ndk_linux/toolchains:aarch64"),
            cc_library("//java/com/google:native_lib")
                .transitive_quote_include_dirs(".", "blaze-out/android-armv7a/genfiles")
                .transitive_system_include_dirs("third_party/java/jdk/include")
                .src("jni/native.cc")
                .dep("//java/com/google:native_lib2", "//android_ndk_linux/toolchains:armv7a"));

    assertTargetMapEquivalence(newTargetMap, oldTargetMap);
  }

  @Test
  public void testJavaTargetMap() throws Exception {
    // Old target map construction.
    TargetMap oldTargetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind(Kind.JAVA_LIBRARY)
                    .addSource(source("java/com/google/ClassWithUniqueName1.java"))
                    .addSource(source("java/com/google/ClassWithUniqueName2.java"))
                    .addDependency("//java/com/google:dep")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setClassJar(gen("import/import.generated_jar")))))
            .build();

    // New target map construction.
    BlazeInfoData info =
        BlazeInfoData.builder().setBlazeBinaryPath("bazel-out/crosstool/bin").build();

    TargetMap newTargetMap =
        targetMap(
            java_library("//java/com/google:lib", info)
                .src("ClassWithUniqueName1.java", "ClassWithUniqueName2.java")
                .dep("//java/com/google:dep")
                .generated_jar("//import/import.generated_jar"));

    assertTargetMapEquivalence(newTargetMap, oldTargetMap);
  }

  @Test
  public void testAndroidTargetMap() throws Exception {
    TargetMap oldTargetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:liba")
                    .setBuildFile(source("import/BUILD"))
                    .setKind(Kind.ANDROID_LIBRARY)
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .setAndroidInfo(AndroidIdeInfo.builder())
                    .addDependency("//import:import")
                    .addDependency("//import:import_android"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind(Kind.ANDROID_LIBRARY)
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder())
                    .setAndroidInfo(AndroidIdeInfo.builder())
                    .addDependency("//import:import")
                    .addDependency("//import:import_android"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:import")
                    .setBuildFile(source("import/BUILD"))
                    .setKind(Kind.JAVA_LIBRARY)
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder().setClassJar(gen("import/import.jar")))))
            .build();

    // New target map construction.
    BlazeInfoData info =
        BlazeInfoData.builder().setBlazeBinaryPath("bazel-out/crosstool/bin").build();

    TargetMap newTargetMap =
        targetMap(
            android_library("//import:liba", info)
                .src("Lib.java")
                .dep("//import:import", "//import:import_android"),
            android_library("//import:lib", info)
                .src("Lib.java")
                .dep("//import:import", "//import:import_android"),
            java_library("//import:import", info).generated_jar("import.jar"));

    assertTargetMapEquivalence(newTargetMap, oldTargetMap);
  }

  private static ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private static ArtifactLocation gen(String relativePath) {
    String fakeGenRootExecutionPathFragment = "bazel-out/crosstool/bin";
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(fakeGenRootExecutionPathFragment)
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  private static void assertTargetMapEquivalence(TargetMap actual, TargetMap expected)
      throws IOException {
    Truth.assertThat(serializeTargetMap(actual)).isEqualTo(serializeTargetMap(expected));
  }

  private static String serializeTargetMap(TargetMap map) throws IOException {
    // We are serializing the target map with Gson because the toString() method of target maps do
    // not print the whole contents of the map.
    // Serializing it to JSON also makes failure comparison a lot better and makes the diff much
    // easier to understand because it preserves
    // the nested object structure of the target map.
    return new GsonBuilder().setPrettyPrinting().create().toJson(map);
  }
}
