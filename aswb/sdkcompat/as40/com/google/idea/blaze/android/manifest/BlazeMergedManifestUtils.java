/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.manifest;

import com.android.manifmerger.ManifestSystemProperty;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.model.AndroidManifestIndex;
import com.android.tools.idea.model.AndroidManifestRawText;
import com.android.tools.idea.projectsystem.ManifestOverrides;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.projectsystem.PackageNameCompat;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.android.dom.manifest.AndroidManifestXmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for computing merged manifest attributes for Blaze Android targets without actually
 * building them.
 */
public final class BlazeMergedManifestUtils {
  /**
   * Determines whether we use the {@link AndroidManifestIndex} to obtain the raw text package name
   * from an Android target's primary manifest. Note that we still won't use the index if {@link
   * AndroidManifestIndex#indexEnabled()} returns false.
   *
   * @see PackageNameCompat#getPackageName(Module)
   * @see PackageNameCompat#doGetPackageName(AndroidFacet, boolean)
   */
  public static final BoolExperiment USE_ANDROID_MANIFEST_INDEX =
      new BoolExperiment("use.android.manifest.index", true);

  private BlazeMergedManifestUtils() {}

  /**
   * Returns the overrides that Blaze applies when generating the merged manifest for the Android
   * target corresponding to the given {@code facet}.
   *
   * @see BlazeMergedManifestUtils#getManifestOverrides(AndroidIdeInfo)
   */
  public static ManifestOverrides getManifestOverrides(AndroidFacet facet) {
    AndroidIdeInfo androidIdeInfo = getAndroidIdeInfo(facet);
    if (androidIdeInfo == null) {
      return new ManifestOverrides();
    }
    return getManifestOverrides(androidIdeInfo);
  }

  /**
   * Returns the overrides that Blaze applies when generating the merged manifest for the given
   * Android target. This all comes from the map specified for the target's "manifest_values"
   * parameter.
   *
   * @see ManifestOverrides
   * @param androidIdeInfo the Android target for which we'll determine manifest override info
   * @return the {@link ManifestOverrides} derived from the target's manifest_values
   */
  public static ManifestOverrides getManifestOverrides(AndroidIdeInfo androidIdeInfo) {
    Map<String, String> manifestValues = androidIdeInfo.getManifestValues();
    ImmutableMap.Builder<ManifestSystemProperty, String> directOverrides = ImmutableMap.builder();
    ImmutableMap.Builder<String, String> placeholders = ImmutableMap.builder();
    manifestValues.forEach(
        (key, value) -> processManifestValue(key, value, directOverrides, placeholders));
    return new ManifestOverrides(directOverrides.build(), placeholders.build());
  }

  /**
   * Puts the key-value pair from a target's manifest_values map into either {@code directOverrides}
   * if the key corresponds to a manifest attribute that Blaze allows you to override directly, or
   * {@code placeholders} otherwise.
   *
   * @see <a
   *     href="https://docs.bazel.build/versions/master/be/android.html#android_binary.manifest_values">manifest_values</a>
   */
  private static void processManifestValue(
      String key,
      String value,
      ImmutableMap.Builder<ManifestSystemProperty, String> directOverrides,
      ImmutableMap.Builder<String, String> placeholders) {
    switch (key) {
      case "applicationId":
        directOverrides.put(ManifestSystemProperty.PACKAGE, value);
        break;
      case "versionCode":
        directOverrides.put(ManifestSystemProperty.VERSION_CODE, value);
        break;
      case "versionName":
        directOverrides.put(ManifestSystemProperty.VERSION_NAME, value);
        break;
      case "minSdkVersion":
        directOverrides.put(ManifestSystemProperty.MIN_SDK_VERSION, value);
        break;
      case "targetSdkVersion":
        directOverrides.put(ManifestSystemProperty.TARGET_SDK_VERSION, value);
        break;
      case "maxSdkVersion":
        directOverrides.put(ManifestSystemProperty.MAX_SDK_VERSION, value);
        break;
      case "packageName":
        // From the doc: "packageName will be ignored and will be set from either applicationId if
        // specified or the package in manifest"
        break;
      default:
        placeholders.put(key, value);
    }
  }

  /**
   * Returns the package name from the merged manifest of the Android target corresponding to the
   * given {@code facet}.
   *
   * <p>The result is cached in the {@code facet}'s user data with a dependencies on {@link
   * BlazeSyncModificationTracker} and the primary manifest of the Android target.
   *
   * @see BlazeMergedManifestUtils#getPackageName(Project, AndroidIdeInfo)
   */
  @Nullable
  public static String getPackageName(AndroidFacet facet) {
    Project project = facet.getModule().getProject();
    return CachedValuesManager.getManager(project)
        .getCachedValue(
            facet,
            () -> {
              ModificationTracker syncTracker = BlazeSyncModificationTracker.getInstance(project);
              AndroidIdeInfo androidIdeInfo = getAndroidIdeInfo(facet);
              if (androidIdeInfo == null) {
                return CachedValueProvider.Result.create(null, syncTracker);
              }
              VirtualFile primaryManifest =
                  SourceProviderManager.getInstance(facet).getMainManifestFile();
              return CachedValueProvider.Result.create(
                  getPackageName(project, androidIdeInfo, primaryManifest),
                  syncTracker,
                  primaryManifest);
            });
  }

  /**
   * Returns the package name from an Android target's merged manifest without actually computing
   * the whole merged manifest.
   *
   * <p>This is either
   *
   * <ol>
   *   <li>The {@link ManifestSystemProperty#PACKAGE} manifest override if one is specified by the
   *       corresponding BUILD target, or
   *   <li>The result of applying placeholder substitution to the raw package name from the module's
   *       primary manifest
   * </ol>
   *
   * @see com.android.tools.idea.projectsystem.AndroidModuleSystem#getManifestOverrides()
   * @see com.android.tools.idea.projectsystem.AndroidModuleSystem#getPackageName()
   */
  @Nullable
  public static String getPackageName(Project project, AndroidIdeInfo androidIdeInfo) {
    return getPackageName(project, androidIdeInfo, null);
  }

  /**
   * Returns the package name from an Android target's merged manifest without actually computing
   * the whole merged manifest. Callers can provide the {@code primaryManifest} if it is known ahead
   * of time, saving us the trouble of extracting the {@link BlazeArtifact} from the Android target
   * and finding the corresponding {@link VirtualFile}.
   *
   * @see BlazeMergedManifestUtils#getPackageName(Project, AndroidIdeInfo)
   */
  @Nullable
  private static String getPackageName(
      Project project, AndroidIdeInfo androidIdeInfo, @Nullable VirtualFile primaryManifest) {
    ManifestOverrides manifestOverrides = getManifestOverrides(androidIdeInfo);
    String packageOverride =
        manifestOverrides.getDirectOverrides().get(ManifestSystemProperty.PACKAGE);
    if (packageOverride != null) {
      return packageOverride;
    }
    String rawPackageName = getRawPackageName(project, androidIdeInfo, primaryManifest);
    return rawPackageName == null
        ? null
        : StringUtil.nullize(manifestOverrides.resolvePlaceholders(rawPackageName));
  }

  @Nullable
  private static String getRawPackageName(
      Project project, AndroidIdeInfo androidIdeInfo, @Nullable VirtualFile primaryManifest) {
    // Try to get a VirtualFile for the primary manifest so we can use the index
    // or PSI-based methods instead of re-parsing the whole manifest.
    if (primaryManifest == null) {
      BlazeArtifact manifestArtifact = getManifestArtifact(project, androidIdeInfo);
      if (manifestArtifact == null) {
        return null;
      }
      primaryManifest = getArtifactAsVirtualFile(manifestArtifact);
      if (primaryManifest == null) {
        return getRawPackageNameFromArtifact(project, manifestArtifact);
      }
    }
    if (USE_ANDROID_MANIFEST_INDEX.getValue()
        && AndroidManifestIndex.indexEnabled()
        // Don't even try querying the index if we're in a dumb read action.
        && !(ApplicationManager.getApplication().isReadAccessAllowed()
            && DumbService.isDumb(project))) {
      String rawPackageName = getRawPackageNameFromIndex(project, primaryManifest);
      // Querying the index for a pre-parsed package name may fail if part of the
      // manifest is malformed, or if this method was called with the read lock
      // and we happen to be indexing. In these cases, we'll fall back to grabbing
      // the package from the root tag of the corresponding PSI, which should be
      // faster than having ManifestParser parse the entire file and may benefit
      // from PSI caching.
      if (rawPackageName != null) {
        return rawPackageName;
      }
    }
    return getRawPackageNameFromPsi(project, primaryManifest);
  }

  @Nullable
  private static String getRawPackageNameFromIndex(Project project, VirtualFile manifest) {
    try {
      AndroidManifestRawText manifestRawText =
          DumbService.getInstance(project)
              .runReadActionInSmartMode(
                  () -> AndroidManifestIndex.getDataForManifestFile(project, manifest));
      return manifestRawText == null ? null : manifestRawText.getPackageName();
    } catch (IndexNotReadyException e) {
      // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
      //  We need to refactor callers to require a *smart* read action, at which point we can
      //  remove this try-catch.
      return null;
    }
  }

  @Nullable
  private static String getRawPackageNameFromPsi(Project project, VirtualFile manifest) {
    return ApplicationManager.getApplication()
        .runReadAction(
            (Computable<String>)
                () -> {
                  PsiFile manifestPsi = AndroidPsiUtils.getPsiFileSafely(project, manifest);
                  if (manifestPsi instanceof XmlFile) {
                    return new AndroidManifestXmlFile((XmlFile) manifestPsi).getPackageName();
                  }
                  return null;
                });
  }

  @Nullable
  private static String getRawPackageNameFromArtifact(
      Project project, BlazeArtifact manifestArtifact) {
    try {
      ManifestParser.ParsedManifest parsedManifest =
          ParsedManifestService.getInstance(project).getParsedManifest(manifestArtifact);
      return parsedManifest == null ? null : parsedManifest.packageName;
    } catch (IOException e) {
      Logger.getInstance(BlazeMergedManifestUtils.class).error(e);
      return null;
    }
  }

  @Nullable
  private static BlazeArtifact getManifestArtifact(Project project, AndroidIdeInfo androidIdeInfo) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    ArtifactLocation manifestLocation = androidIdeInfo.getManifest();
    if (manifestLocation == null) {
      return null;
    }
    return projectData.getArtifactLocationDecoder().resolveOutput(manifestLocation);
  }

  @Nullable
  private static VirtualFile getArtifactAsVirtualFile(BlazeArtifact manifestArtifact) {
    if (!(manifestArtifact instanceof BlazeArtifact.LocalFileArtifact)) {
      return null;
    }
    File manifestFile = ((BlazeArtifact.LocalFileArtifact) manifestArtifact).getFile();
    return VfsUtil.findFileByIoFile(manifestFile, false);
  }

  @Nullable
  public static AndroidIdeInfo getAndroidIdeInfo(AndroidFacet facet) {
    Project project = facet.getModule().getProject();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    TargetKey targetKey =
        AndroidResourceModuleRegistry.getInstance(project).getTargetKey(facet.getModule());
    if (targetKey == null) {
      return null;
    }
    TargetIdeInfo target = projectData.getTargetMap().get(targetKey);
    if (target == null) {
      return null;
    }
    return target.getAndroidIdeInfo();
  }
}
