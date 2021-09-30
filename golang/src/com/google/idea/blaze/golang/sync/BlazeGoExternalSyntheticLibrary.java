/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.sync;

import com.goide.GoIcons;
import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalSyntheticLibrary;
import com.intellij.openapi.roots.SyntheticLibrary;
import java.io.File;
import java.util.Collection;
import javax.swing.Icon;

/**
 * A {@link BlazeExternalSyntheticLibrary} for Go external libraries. Only supports one instance
 * per importPath. 
 */
public final class BlazeGoExternalSyntheticLibrary extends BlazeExternalSyntheticLibrary {
    private final ImmutableMultimap<String, File> importpathToFilesMap;
    private final String importPath;
    private final boolean isRoot;

    /**
    * Constructs a root Go external library. Holds a flat list of all external source files. Later
    * processed by {@link BlazeGoTreeStructureProvider} to structure external source files based on
    * their importpath.
    */
    public BlazeGoExternalSyntheticLibrary(String presentableText, ImmutableMultimap<String, File> importpathToFilesMap) {
        super(presentableText, importpathToFilesMap.values());
        this.importpathToFilesMap = importpathToFilesMap;
        this.importPath = presentableText;
        this.isRoot = true;
    }

    /**
    * Constructs a non-root Go external library. Holds the external source files for an external directory.
    * Intended for use by {@link GoSyntheticLibraryElementNode}.
    */
    public BlazeGoExternalSyntheticLibrary(String presentableText, String importPath, Collection<File> files) {
        super(presentableText, files);
        this.importpathToFilesMap = null;
        this.importPath = importPath;
        this.isRoot = false;
    }

    public boolean isRoot() {
        return this.isRoot;
    }

    public ImmutableMultimap<String, File> getImportpathToFilesMap() {
        return importpathToFilesMap;
    }

    @Override
    public Icon getIcon(boolean unused) {
        return GoIcons.VENDOR_DIRECTORY;
    }

    @Override
    public boolean equals(Object o) {
        // intended to be only a single instance added to the project for each value of importPath
        return o instanceof BlazeGoExternalSyntheticLibrary
            && importPath.equals(((BlazeGoExternalSyntheticLibrary) o).importPath);
    }

    @Override
    public int hashCode() {
        // intended to be only a single instance added to the project for each value of importPath
        return importPath.hashCode();
    }
}
