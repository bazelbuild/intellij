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
package com.google.idea.blaze.kotlin.sync;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

@RunWith(JUnit4.class)
public class BlazeKotlinStdLibTest {
    @Test
    public void testIjarMatching() {
        Arrays.asList(
                "kotlin-runtime-1.2.0-ijar_26f7aee9",
                "kotlin-runtime-ijar_26f7aee9",
                "kotlin-stdlib-ijar_d200065a",
                "kotlin-stdlib-1.2.0-ijar_d200065a",
                "kotlin-stdlib-jdk7-ijar_ede101b0",
                "kotlin-stdlib-jdk7-1.2.0-ijar_ede101b0",
                "kotlin-stdlib-jdk8-1.2.0-ijar_8a5d1590",
                "kotlin-stdlib-jdk8-1.2.10-ijar_8a5d1590"
        ).forEach(libName ->
                Assert.assertTrue(libName + " did not match as kotlin runtime ijar",
                        BlazeKotlinStdLib.isKotlinStdLibIjar.test(libName))
        );


    }
}
