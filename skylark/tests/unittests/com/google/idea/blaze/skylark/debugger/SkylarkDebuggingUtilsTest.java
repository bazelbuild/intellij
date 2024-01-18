/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.skylark.debugger;

import static org.junit.Assert.fail;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SkylarkDebuggingUtils}
 */
@RunWith(JUnit4.class)
public class SkylarkDebuggingUtilsTest extends BlazeTestCase {

    @Test
    public void testDoesntCrashOnUninitializedProject() {
        try {
            SkylarkDebuggingUtils.debuggingEnabled(getProject());
        } catch (IllegalStateException e) {
            fail("Debugging enabled check failed for uninitialized project.");
        }
    }
}
