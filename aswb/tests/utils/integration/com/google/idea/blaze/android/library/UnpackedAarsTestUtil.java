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
package com.google.idea.blaze.android.library;

import com.android.SdkConstants;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Utils for getting aar cache directory information in test. Sometimes tests need to bypass some
 * parameter and find local cached aar information directly. We do not want to leave such test only
 * helper function in prod. Then they are moved here.
 */
public final class UnpackedAarsTestUtil {

  /**
   * Returns the res/ directory corresponding to an unpacked AAR file. This function only works with
   */
  @Nullable
  public static File getResourceDirectory(File aarDir) {
    return aarDir == null ? aarDir : new File(aarDir, SdkConstants.FD_RES);
  }

  private UnpackedAarsTestUtil() {}
}
