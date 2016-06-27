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

import com.intellij.TestCaseLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.TeamCityLogger;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.util.ArrayUtil;

import junit.framework.JUnit4TestAdapter;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A cut-down version of {@link com.intellij.TestAll} which supports test classes inside jars.
 */
@TestAggregator
public class TestAll implements Test {

  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private final TestCaseLoader testCaseLoader;

  public TestAll(String packageRoot) throws Throwable {
    this(packageRoot, getClassRoots());
  }

  public TestAll(String packageRoot, String... classRoots) throws IOException, ClassNotFoundException {
    testCaseLoader = new TestCaseLoader("");
    fillTestCases(testCaseLoader, packageRoot, classRoots);
  }

  public static String[] getClassRoots() {
    final ClassLoader loader = TestAll.class.getClassLoader();
    if (loader instanceof URLClassLoader) {
      return getClassRoots(((URLClassLoader)loader).getURLs());
    }
    final Class<? extends ClassLoader> loaderClass = loader.getClass();
    if (loaderClass.getName().equals("com.intellij.util.lang.UrlClassLoader")) {
      try {
        final Method declaredMethod = loaderClass.getDeclaredMethod("getBaseUrls");
        final List<URL> urls = (List<URL>) declaredMethod.invoke(loader);
        return getClassRoots(urls.toArray(new URL[urls.size()]));
      } catch (Throwable ignore) {
      }
    }
    return System.getProperty("java.class.path").split(File.pathSeparator);
  }

  private static String[] getClassRoots(URL[] urls) {
    return Arrays.stream(urls)
      .map(VfsUtilCore::convertFromUrl)
      .map(VfsUtilCore::urlToPath)
      .toArray(String[]::new);
  }

  private static boolean isIntellijPlatformJar(String classRoot) {
    return classRoot.contains("intellij-platform-sdk");
  }

  public static void fillTestCases(TestCaseLoader testCaseLoader, String packageRoot, String... classRoots) throws IOException {
    long before = System.currentTimeMillis();
    for (String classRoot : classRoots) {
      if (isIntellijPlatformJar(classRoot)) {
        continue;
      }
      int oldCount = testCaseLoader.getClasses().size();
      File classRootFile = new File(FileUtil.toSystemDependentName(classRoot));
      Collection<String> classes = TestClassFinder.findTestClasses(classRootFile, packageRoot);
      testCaseLoader.loadTestCases(classRootFile.getName(), classes);
      int newCount = testCaseLoader.getClasses().size();
      if (newCount != oldCount) {
        System.out.println("Loaded " + (newCount - oldCount) + " tests from class root " + classRoot);
      }
    }

    if (testCaseLoader.getClasses().size() == 1) {
      testCaseLoader.clearClasses();
    }
    long after = System.currentTimeMillis();

    String message = "Number of test classes found: " + testCaseLoader.getClasses().size()
                      + " time to load: " + (after - before) / 1000 + "s.";
    System.out.println(message);
    log(message);
  }

  @Override
  public int countTestCases() {
    int count = 0;
    for (Object aClass : testCaseLoader.getClasses()) {
      Test test = getTest((Class)aClass);
      if (test != null) {
        count += test.countTestCases();
      }
    }
    return count;
  }

  @Override
  public void run(final TestResult testResult) {
    List<Class> classes = testCaseLoader.getClasses();
    for (Class<?> aClass : classes) {
      runTest(testResult, aClass);
      if (testResult.shouldStop()) {
        break;
      }
    }
  }

  private void runTest(final TestResult testResult, Class testCaseClass) {
    Test test = getTest(testCaseClass);
    if (test == null) {
      return;
    }

    try {
      test.run(testResult);
    } catch (Throwable t) {
      testResult.addError(test, t);
    }
  }

  @Nullable
  private static Test getTest(final Class<?> testCaseClass) {
    try {
      if ((testCaseClass.getModifiers() & Modifier.PUBLIC) == 0) {
        return null;
      }
      if (testCaseClass.isAnnotationPresent(TestAggregator.class)) {
        // prevent infinite loops in 'countTestCases'
        return null;
      }

      Method suiteMethod = safeFindMethod(testCaseClass, "suite");
      if (suiteMethod != null) {
        return (Test) suiteMethod.invoke(null, (Object[]) ArrayUtil.EMPTY_CLASS_ARRAY);
      }

      if (TestRunnerUtil.isJUnit4TestClass(testCaseClass)) {
        return new JUnit4TestAdapter(testCaseClass);
      }

      final int[] testsCount = {0};
      TestSuite suite = new TestSuite(testCaseClass) {
        @Override
        public void addTest(Test test) {
          if (!(test instanceof TestCase)) {
            doAddTest(test);
          }
          else {
            String name = ((TestCase)test).getName();
            if ("warning".equals(name)) {
              return; // Mute TestSuite's "no tests found" warning
            }
            doAddTest(test);
          }
        }

        private void doAddTest(Test test) {
          testsCount[0]++;
          super.addTest(test);
        }
      };

      return testsCount[0] > 0 ? suite : null;
    }
    catch (Throwable t) {
      System.err.println("Failed to load test: " + testCaseClass.getName());
      t.printStackTrace(System.err);
      return null;
    }
  }

  @Nullable
  private static Method safeFindMethod(Class<?> klass, String name) {
    try {
      return klass.getMethod(name);
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static void log(String message) {
    TeamCityLogger.info(message);
  }

}
