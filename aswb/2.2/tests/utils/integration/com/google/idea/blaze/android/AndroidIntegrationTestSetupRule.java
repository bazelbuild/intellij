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
package com.google.idea.blaze.android;

import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.PlatformUtils;
import org.junit.rules.ExternalResource;

/**
 * Runs before Android Studio integration tests, to ensure the AndroidStudio platform prefix is
 * honored.
 */
public class AndroidIntegrationTestSetupRule extends ExternalResource {

  @Override
  protected void before() throws Throwable {
    // We require idea.platform.prefix to be defined before running tests.
    // If we don't call this before setting up the test fixture, IntelliJ ignores
    // the existing value and tries a limited set of candidate prefixes until it finds
    // a matching descriptor for one of them. Notably, "AndroidStudio" is not a candidate.
    // The first parameter doesn't matter in our case, so we pass a nonexistent class name.
    PlatformTestCase.initPlatformPrefix("", System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY));
    System.setProperty("android.studio.sdk.manager.disabled", "true");
    // TODO: Remove the above once we build for a version where "AndroidStudio" is a candidate.
  }
}
