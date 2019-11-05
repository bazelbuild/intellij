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
package com.google.idea.blaze.android.projectsystem;

import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.utils.concurrency.AsyncSupplier;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.dom.manifest.AndroidManifestXmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

/** Utilities to obtain the package name for a given module. #api3.5 */
public class PackageNameCompat {
  @Nullable
  public static String getPackageName(Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    return CachedValuesManager.getManager(module.getProject())
        .getCachedValue(
            facet,
            () -> {
              String packageName = StringUtil.nullize(doGetPackageName(facet), true);
              return CachedValueProvider.Result.create(
                  packageName, MergedManifestManager.getModificationTracker(module));
            });
  }

  @Nullable
  @SuppressWarnings("FutureReturnValueIgnored") // for the supplier.get() which is simply a trigger
  private static String doGetPackageName(AndroidFacet facet) {
    // It's possible for Blaze to override the module's package name, so we have to check the merged
    // manifest.
    AsyncSupplier<MergedManifestSnapshot> supplier =
        MergedManifestManager.getMergedManifestSupplier(facet.getModule());
    MergedManifestSnapshot mergedManifest = supplier.getNow();
    if (mergedManifest != null) {
      if (mergedManifest.isValid()) {
        return mergedManifest.getPackage();
      }
    } else {
      // We might be on the EDT, so we can't block on computing the merged manifest. But we *can*
      // ensure that the computation
      // is running in the background so that this module's merged manifest will be available to
      // some future caller.
      supplier.get();
    }
    // Since we can't use the merged manifest yet, we'll resort to manually parsing the PSI of the
    // primary manifest for now.
    AndroidManifestXmlFile primaryManifest = AndroidManifestUtils.getPrimaryManifestXml(facet);
    return primaryManifest == null ? null : primaryManifest.getPackageName();
  }
}
