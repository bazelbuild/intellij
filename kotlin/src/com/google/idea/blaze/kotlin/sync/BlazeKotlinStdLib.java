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
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.kotlin.BlazeKotlin;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.kotlin.idea.framework.CommonLibraryKind;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.idea.blaze.kotlin.BlazeKotlin.COMPILER_WORKSPACE_NAME;


public enum BlazeKotlinStdLib {
    STDLIB("stdlib", true),
    STDLIB_JDK7("stdlib-jdk7", true),
    STDLIB_JDK8("stdlib-jdk8", true),
    REFLECT("reflect", false),
    TEST("test", false);

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

    static final ImmutableMap<String, BlazeKotlinStdLib> OPTIONAL_STDLIBS =
            libsMatching(lib -> !lib.mandatory).collect(ImmutableMap.toImmutableMap(lib -> lib.id, lib -> lib));

    public static final ImmutableMap<String, BlazeKotlinStdLib> MANDATORY_STDLIBS =
            libsMatching(lib -> lib.mandatory).collect(ImmutableMap.toImmutableMap(lib -> lib.id, lib -> lib));

    public static final Predicate<String> isKotlinStdLibIjar =
            Pattern.compile("kotlin-(?:runtime-|stdlib-)(?:[^-]+-)?(?:\\d+.\\d+.\\d+-)?ijar.*").asPredicate();

    @SuppressWarnings("SameParameterValue")
    static Library prepareIJLibrarySet(
            LibraryTable.ModifiableModel modifiableTable,
            Path root,
            boolean includeClasses,
            Collection<BlazeKotlinStdLib> libs
    ) throws Exception {
        final Path libRoot = root.resolve("lib");
        Library libRef = modifiableTable.createLibrary("KotlinJavaRuntime", CommonLibraryKind.INSTANCE);
        Library.ModifiableModel modifiableLibrary = libRef.getModifiableModel();

        try {
            libs.stream().map(lib -> lib.id).forEach(libId -> {
                if (includeClasses) {
                    validateAddRoot(modifiableLibrary, libRoot, "kotlin-" + libId + ".jar", OrderRootType.CLASSES);
                }
                validateAddRoot(modifiableLibrary, libRoot, "kotlin-" + libId + "-sources.jar", OrderRootType.SOURCES);
            });
        } catch (RuntimeException rte) {
            throw new Exception(rte.getMessage());
        }

        modifiableLibrary.commit();
        modifiableTable.commit();
        return libRef;
    }

    private static void validateAddRoot(Library.ModifiableModel library, Path root, String libFileName, OrderRootType type) throws RuntimeException {
        File libraryFile = root.resolve(libFileName).toFile();
        if (!libraryFile.exists() || !libraryFile.isFile()) {
            throw new RuntimeException("kotlin stdlib " + libFileName + " not available or invalid.");
        }
        library.addRoot(VfsUtil.getUrlForLibraryRoot(libraryFile), type);
    }

    private static Stream<BlazeKotlinStdLib> libsMatching(Predicate<BlazeKotlinStdLib> predicate) {
        return Arrays.stream(BlazeKotlinStdLib.values()).filter(predicate);
    }

    private static ArtifactLocation.Builder createExternalArtifactBuilder(String rootExecutionPathFragment) {
        return ArtifactLocation.builder()
                .setRootExecutionPathFragment(rootExecutionPathFragment)
                .setIsExternal(true);
    }

    static ImmutableList<BlazeJarLibrary> prepareBlazeLibraries(@Nullable Collection<BlazeKotlinStdLib> libs) {
        String root = "external/" + COMPILER_WORKSPACE_NAME;

        // for some reason this could be null in the prefetch stage.
        if(libs == null) {
            return ImmutableList.of();
        }

        return libs.stream()
                .map(x -> x.id).map(id -> {
                    LibraryArtifact.Builder libBuilder = LibraryArtifact.builder();
                    libBuilder.setClassJar(createExternalArtifactBuilder(root).setRelativePath("lib/kotlin-" + id + ".jar").setIsSource(false).build());
                    // RFC: The kotlin plugin is unaware of sources addded this way. This is why we need to build an entry in the library table.
                    libBuilder.addSourceJar(createExternalArtifactBuilder(root).setRelativePath("lib/kotlin-" + id + "-sources.jar").setIsSource(true).build());
                    return new BlazeJarLibrary(libBuilder.build());
                })
                .collect(ImmutableList.toImmutableList());
    }
}
