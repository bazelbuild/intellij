/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.kotlin.BlazeKotlin;

import javax.annotation.Nullable;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.idea.blaze.kotlin.BlazeKotlin.COMPILER_WORKSPACE_NAME;


public enum BlazeKotlinStdLib {
    STDLIB("kotlin-stdlib", true),
    STDLIB_JDK7("kotlin-stdlib-jdk7", true),
    STDLIB_JDK8("kotlin-stdlib-jdk8", true),
    REFLECT("kotlin-reflect", false),
    TEST("kotlin-test", false);

    public final String id;

    /**
     * mandatory libraries are those that should always be present, the blaze plugin picks these  up from the external workspace located at
     * {@link BlazeKotlin#COMPILER_WORKSPACE_NAME} which is loaded by the rules found at {@link BlazeKotlin#RULES_REPO} loaded into the workspace with name
     * {@link BlazeKotlin#KOTLIN_RULES_WORKSPACE_NAME}.
     */
    public final boolean mandatory;

    BlazeKotlinStdLib(String id, boolean mandatory) {
        this.id = id;
        this.mandatory = mandatory;
    }

    String getClassJarFileName() {
        return id + ".jar";
    }

    String getSourceJarFileName() {
        return id + "-sources.jar";
    }

    String externalWorkspaceRelativeLibPath(Supplier<String> jarName) {
        return Paths.get("lib", jarName.get()).toString();
    }

    static final String COMPILER_WORKSPACE_PATH = Paths.get("external", COMPILER_WORKSPACE_NAME).toString();

    public static final ImmutableList<BlazeKotlinStdLib> MANDATORY_STDLIBS =
            libsMatching(lib -> lib.mandatory).collect(ImmutableList.toImmutableList());

    private static Stream<BlazeKotlinStdLib> libsMatching(Predicate<BlazeKotlinStdLib> predicate) {
        return Arrays.stream(BlazeKotlinStdLib.values()).filter(predicate);
    }

    private static ArtifactLocation.Builder createExternalArtifactBuilder(String rootExecutionPathFragment) {
        return ArtifactLocation.builder()
                .setRootExecutionPathFragment(rootExecutionPathFragment)
                .setIsExternal(true);
    }

    public static boolean isStdLib(TargetIdeInfo target) {
        final Label label = target.key.label;
        return target.kind == Kind.KOTLIN_STDLIB &&
                label.externalWorkspaceName() != null &&
                label.externalWorkspaceName().equals(COMPILER_WORKSPACE_NAME);
    }

    public static ImmutableList<BlazeJarLibrary> prepareBlazeLibraries(@Nullable Collection<BlazeKotlinStdLib> libs) {
        // for some reason this could be null in the prefetch stage.
        if (libs == null) {
            return ImmutableList.of();
        }

        return libs.stream()
                .map(lib -> {
                    LibraryArtifact.Builder libBuilder = LibraryArtifact.builder();
                    libBuilder.setClassJar(
                            createExternalArtifactBuilder(COMPILER_WORKSPACE_PATH)
                                    .setRelativePath(lib.externalWorkspaceRelativeLibPath(lib::getClassJarFileName))
                                    .setIsSource(false)
                                    .build()
                    );
                    libBuilder.addSourceJar(
                            createExternalArtifactBuilder(COMPILER_WORKSPACE_PATH)
                                    .setRelativePath(lib.externalWorkspaceRelativeLibPath(lib::getSourceJarFileName))
                                    .setIsSource(true)
                                    .build()
                    );
                    return new BlazeJarLibrary(libBuilder.build());
                }).collect(ImmutableList.toImmutableList());
    }
}
