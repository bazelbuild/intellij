/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.sdklegacy;

import com.android.SdkConstants;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionHelper;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.annotation.Nullable;

/** Utility methods for handling the android sdk. */
@Deprecated
final class BlazeAndroidSdk {
  private static final Logger LOG = Logger.getInstance(BlazeAndroidSdk.class);

  private BlazeAndroidSdk() {}

  /** Reads the android sdk level from your local SDK directory. */
  public static String getAndroidSdkLevelFromLocalChannel(
      String localSdkLocation, String androidSdkPlatform) {
    File androidSdkPlatformsDir =
        new File(new File(new File(localSdkLocation), "platforms"), androidSdkPlatform);
    File sourcePropertiesFile = new File(androidSdkPlatformsDir, SdkConstants.FN_SOURCE_PROP);
    return getAndroidSdkLevelFromSourceProperties(sourcePropertiesFile);
  }

  @Nullable
  public static String getAndroidSdkLevelFromSourceProperties(File sourcePropertiesFile) {
    if (!sourcePropertiesFile.exists()) {
      return null;
    }

    AndroidVersion androidVersion =
        readAndroidVersionFromSourcePropertiesFile(sourcePropertiesFile);
    if (androidVersion == null) {
      LOG.warn("Could not read source.properties from: " + sourcePropertiesFile);
      return null;
    }
    return AndroidTargetHash.getPlatformHashString(androidVersion);
  }

  @Nullable
  private static AndroidVersion readAndroidVersionFromSourcePropertiesFile(
      File sourcePropertiesFile) {
    Properties props = parseProperties(sourcePropertiesFile);
    if (props == null) {
      return null;
    }
    try {
      return AndroidVersionHelper.create(props);
    } catch (AndroidVersion.AndroidVersionException e) {
      return null;
    }
  }

  /**
   * Parses the given file as properties file if it exists. Returns null if the file does not exist,
   * cannot be parsed or has no properties.
   */
  @Nullable
  private static Properties parseProperties(File propsFile) {
    if (!propsFile.exists()) {
      return null;
    }
    try (InputStream fis = new FileInputStream(propsFile)) {
      Properties props = new Properties();
      props.load(fis);

      // To be valid, there must be at least one property in it.
      if (props.size() > 0) {
        return props;
      }
    } catch (IOException e) {
      // Ignore
    }
    return null;
  }
}
