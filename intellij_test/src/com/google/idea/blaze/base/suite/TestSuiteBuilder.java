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

import com.google.common.base.Strings;
import com.google.idea.blaze.base.BlazeTestSystemProperties;

import junit.framework.Test;
import junit.framework.TestSuite;

@TestAggregator
public class TestSuiteBuilder {
  public static Test suite() throws Throwable {

    BlazeTestSystemProperties.configureSystemProperties();

    String packageRoot = System.getProperty("idea.test.package.root");
    packageRoot = Strings.nullToEmpty(packageRoot);

    TestSuite suite = new TestSuite();
    suite.addTest(new TestAll(packageRoot));
    return suite;
  }

}
