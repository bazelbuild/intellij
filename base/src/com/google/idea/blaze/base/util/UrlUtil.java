/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.util;

import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.URLUtil;
import java.io.File;

/** Utility methods for converting between URLs and file paths. */
public class UrlUtil {

  public static File urlToFile(String url) {
    return new File(VirtualFileManager.extractPath(url));
  }

  public static String fileToIdeaUrl(File path) {
    return pathToUrl(toSystemIndependentName(path.getPath()));
  }

  public static String pathToUrl(String filePath) {
    filePath = FileUtil.toSystemIndependentName(filePath);
    if (filePath.endsWith(".srcjar") || filePath.endsWith(".jar")) {
      return URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + filePath + URLUtil.JAR_SEPARATOR;
    } else if (filePath.contains("src.jar!")) {
      return URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + filePath;
    } else {
      return VirtualFileManager.constructUrl(
          VirtualFileSystemProvider.getInstance().getSystem().getProtocol(), filePath);
    }
  }

  private static String toSystemIndependentName(String aFileName) {
    return FileUtilRt.toSystemIndependentName(aFileName);
  }
}
