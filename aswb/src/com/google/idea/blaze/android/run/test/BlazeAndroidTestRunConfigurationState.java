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
package com.google.idea.blaze.android.run.test;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;

/**
 * State specific for the android test configuration.
 */
final class BlazeAndroidTestRunConfigurationState implements JDOMExternalizable {

  private static final String RUN_THROUGH_BLAZE_ATTR = "blaze-run-through-blaze";

  public static final int TEST_ALL_IN_MODULE = 0;
  public static final int TEST_ALL_IN_PACKAGE = 1;
  public static final int TEST_CLASS = 2;
  public static final int TEST_METHOD = 3;

  // We reinterpret Android Studio's test mode for running "all tests in a module" (all the tests in the installed test APK) as running all
  // the tests in a rule.
  public static final int TEST_ALL_IN_TARGET = TEST_ALL_IN_MODULE;

  public int TESTING_TYPE = TEST_ALL_IN_MODULE;
  public String INSTRUMENTATION_RUNNER_CLASS = InstrumentationRunnerProvider.getDefaultInstrumentationRunnerClass();
  public String METHOD_NAME = "";
  public String CLASS_NAME = "";
  public String PACKAGE_NAME = "";
  public String EXTRA_OPTIONS = "";

  // Whether to delegate to 'blaze test'.
  private boolean runThroughBlaze;

  @Contract(pure = true)
  boolean isRunThroughBlaze() {
    return runThroughBlaze;
  }

  void setRunThroughBlaze(boolean runThroughBlaze) {
    this.runThroughBlaze = runThroughBlaze;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);

    runThroughBlaze = Boolean.parseBoolean(element.getAttributeValue(RUN_THROUGH_BLAZE_ATTR));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);

    element.setAttribute(RUN_THROUGH_BLAZE_ATTR, Boolean.toString(runThroughBlaze));
  }
}
