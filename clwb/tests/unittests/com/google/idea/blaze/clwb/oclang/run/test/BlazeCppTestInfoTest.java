/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb.oclang.run.test;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.clwb.oclang.run.test.BlazeCppTestInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCppTestInfo}. */
@RunWith(JUnit4.class)
public class BlazeCppTestInfoTest {

  // Patterns (parts in [] are optional):
  // [instantiation/]suite[/suiteorder][::test[/testorder]]

  @Test
  public void testSuite() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(SmRunnerUtils.GENERIC_TEST_PROTOCOL, "suite");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isNull();
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isNull();

    assertThat(testInfo.suiteComponent()).isEqualTo("suite");
    assertThat(testInfo.methodComponent()).isNull();
  }

  @Test
  public void testSuiteOrder() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(SmRunnerUtils.GENERIC_TEST_PROTOCOL, "suite/1");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isNull();
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isNull();

    assertThat(testInfo.suiteComponent()).isEqualTo("suite/1");
    assertThat(testInfo.methodComponent()).isNull();
  }

  @Test
  public void testInstantiationAndSuite() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(SmRunnerUtils.GENERIC_TEST_PROTOCOL, "instantiation/suite");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isEqualTo("instantiation");
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isNull();

    assertThat(testInfo.suiteComponent()).isEqualTo("instantiation/suite");
    assertThat(testInfo.methodComponent()).isNull();
  }

  @Test
  public void testInstantiationAndSuiteOrder() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(SmRunnerUtils.GENERIC_TEST_PROTOCOL, "instantiation/suite/1");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isEqualTo("instantiation");
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isNull();

    assertThat(testInfo.suiteComponent()).isEqualTo("instantiation/suite/1");
    assertThat(testInfo.methodComponent()).isNull();
  }

  @Test
  public void testSuiteAndMethod() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(SmRunnerUtils.GENERIC_TEST_PROTOCOL, "suite::method");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isNull();
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isEqualTo("method");

    assertThat(testInfo.suiteComponent()).isEqualTo("suite");
    assertThat(testInfo.methodComponent()).isEqualTo("method");
  }

  @Test
  public void testSuiteOrderAndMethodOrder() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(SmRunnerUtils.GENERIC_TEST_PROTOCOL, "suite/1::method/2");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isNull();
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isEqualTo("method");

    assertThat(testInfo.suiteComponent()).isEqualTo("suite/1");
    assertThat(testInfo.methodComponent()).isEqualTo("method/2");
  }

  @Test
  public void testInstantiationAndSuiteAndMethod() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(
            SmRunnerUtils.GENERIC_TEST_PROTOCOL, "instantiation/suite::method");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isEqualTo("instantiation");
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isEqualTo("method");

    assertThat(testInfo.suiteComponent()).isEqualTo("instantiation/suite");
    assertThat(testInfo.methodComponent()).isEqualTo("method");
  }

  @Test
  public void testInstantiationAndSuiteOrderAndMethodOrder() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(
            SmRunnerUtils.GENERIC_TEST_PROTOCOL, "instantiation/suite/1::method/2");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isEqualTo("instantiation");
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isEqualTo("method");

    assertThat(testInfo.suiteComponent()).isEqualTo("instantiation/suite/1");
    assertThat(testInfo.methodComponent()).isEqualTo("method/2");
  }

  @Test
  public void testSuiteProtocol() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(
            SmRunnerUtils.GENERIC_SUITE_PROTOCOL, "instantiation/suite/1::method/2");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isEqualTo("instantiation");
    assertThat(testInfo.suite).isEqualTo("suite");
    assertThat(testInfo.method).isEqualTo("method");

    assertThat(testInfo.suiteComponent()).isEqualTo("instantiation/suite/1");
    assertThat(testInfo.methodComponent()).isEqualTo("method/2");
  }

  @Test
  public void testUnknownProtocol() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath("unknown:protocol", "instantiation/suite/1::method/2");

    assertThat(testInfo).isNull();
  }

  @Test
  public void testNumerics() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(
            SmRunnerUtils.GENERIC_TEST_PROTOCOL, "instantiation3/suite4/1::method5/2");

    assertThat(testInfo).isNotNull();
    assertThat(testInfo.instantiation).isEqualTo("instantiation3");
    assertThat(testInfo.suite).isEqualTo("suite4");
    assertThat(testInfo.method).isEqualTo("method5");

    assertThat(testInfo.suiteComponent()).isEqualTo("instantiation3/suite4/1");
    assertThat(testInfo.methodComponent()).isEqualTo("method5/2");
  }

  @Test
  public void testNumericsFail() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(
            SmRunnerUtils.GENERIC_TEST_PROTOCOL, "1instantiation/suite/1::method/2");
    assertThat(testInfo).isNull();
  }

  @Test
  public void testMalformed() {
    BlazeCppTestInfo testInfo =
        BlazeCppTestInfo.fromPath(SmRunnerUtils.GENERIC_TEST_PROTOCOL, "instantiation/suite/1::");
    assertThat(testInfo).isNull();
  }
}
