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
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinSyncData;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Library contributions from Kotlin Targets. The Kotlin std library elements are loaded directly from the workspace whenever the Kotlin language is active,
 * Ijar variants transitively pulled in from other languages are filtered out.
 */
// A Kotlin Toolchain would be a more consistent mechanism method for solving this problem.
public class BlazeKotlinLibrarySource extends LibrarySource.Adapter {
    private final BlazeProjectData blazeProjectData;

    BlazeKotlinLibrarySource(BlazeProjectData blazeProjectData) {
        this.blazeProjectData = blazeProjectData;
    }

    private static boolean isKotlinStdlibIjar(BlazeLibrary lib) {
        return BlazeKotlinStdLib.isKotlinStdLibIjar.test(lib.key.getIntelliJLibraryName());
    }

    /**
     * This filter needs to be installed to remove kotlin stdlib IJars transitively picked up from other java languages. There is likely a better solution in
     * the long term via providers / toolchains.
     */
    @Override
    public Predicate<BlazeLibrary> getLibraryFilter() {
        return lib -> !isKotlinStdlibIjar(lib);
    }

    @Override
    public List<? extends BlazeLibrary> getLibraries() {
        BlazeKotlinSyncData syncData = BlazeKotlinSyncData.get(blazeProjectData);
        assert syncData != null;
        List<BlazeLibrary> fromSyncData = syncData.importResult.libraries.values().stream()
                .filter(lib -> !isKotlinStdlibIjar(lib))
                .collect(Collectors.toList());
        fromSyncData.addAll(BlazeKotlinStdLib.prepareBlazeLibraries(syncData.importResult.stdLibs));
        return ImmutableList.copyOf(fromSyncData);
    }
}
