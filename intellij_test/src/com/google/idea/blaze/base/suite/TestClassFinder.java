/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.suite;

import com.google.common.collect.Sets;
import com.intellij.ClassFinder;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.SortedSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds all valid test classes inside a given directory or jar. */
@TestAggregator
public class TestClassFinder {

  private static final String CLASS_EXTENSION = ".class";

  /** Returns all top-level test classes underneath the specified classpath and package roots. */
  public static SortedSet<String> findTestClasses(File classRootFile, String packageRoot)
      throws IOException {
    if (isJar(classRootFile.getPath())) {
      return findTestClassesInJar(classRootFile, packageRoot);
    }
    ClassFinder finder = new ClassFinder(classRootFile, packageRoot, true);
    return Sets.newTreeSet(finder.getClasses());
  }

  private static SortedSet<String> findTestClassesInJar(File classPathRoot, String packageRoot)
      throws IOException {
    packageRoot = packageRoot.replace('.', File.separatorChar);
    SortedSet<String> classNames = Sets.newTreeSet();
    ZipFile zipFile = new ZipFile(classPathRoot.getPath());
    if (!packageRoot.isEmpty() && zipFile.getEntry(packageRoot) == null) {
      return Sets.newTreeSet();
    }
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      String entryName = entries.nextElement().getName();
      if (entryName.endsWith(CLASS_EXTENSION)
          && isTopLevelClass(entryName)
          && entryName.startsWith(packageRoot)) {
        classNames.add(getClassName(entryName));
      }
    }
    return classNames;
  }

  private static boolean isJar(String filePath) {
    return filePath.endsWith(".jar");
  }

  private static boolean isTopLevelClass(String fileName) {
    return fileName.indexOf('$') < 0;
  }

  /** Given the absolute path of a class file, return the class name. */
  private static String getClassName(String className) {
    return StringUtil.trimEnd(className, CLASS_EXTENSION).replace(File.separatorChar, '.');
  }
}
