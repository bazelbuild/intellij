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
package com.google.idea.blaze.ext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.idea.blaze.ext.IntelliJExtService.ServiceStatus;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IntelliJExtServiceTest {

  private static final String SERVER =
      "ext/IntelliJExtTestServer_deploy.jar";
  public static final String TEST_VERSION = "test server [1.0]";

  @Test
  public void testSimpleGetVersion() throws Exception {
    IntelliJExtService service = new IntelliJExtService(Paths.get(SERVER));
    assertEquals(TEST_VERSION, service.getVersion());
    assertEquals(ServiceStatus.READY, service.getServiceStatus());
  }

  @Test
  public void testServerFailedToInitialize() {
    IntelliJExtService service = new IntelliJExtService(Paths.get(SERVER));
    service.additionalServerArguments("--fail_to_initialize");
    try {
      String unused = service.getVersion();
      Assert.fail("Exception was not thrown");
    } catch (Exception e) {
      // Expected
    }
    assertEquals(ServiceStatus.FAILED, service.getServiceStatus());
  }

  @Test
  public void testServerCrashes() throws Exception {
    IntelliJExtService service = new IntelliJExtService(Paths.get(SERVER));
    assertEquals(TEST_VERSION, service.getVersion());
    String pid = service.getStatus().get("pid");
    Optional<ProcessHandle> process = ProcessHandle.of(Integer.valueOf(pid));
    assertTrue(process.isPresent());
    process.get().destroyForcibly();
    // Server should restart nicely.
    assertEquals(TEST_VERSION, service.getVersion());
    assertNotEquals(pid, service.getStatus().get("pid"));
  }

  @Test
  public void testServerDoesNotRestart() throws Exception {
    IntelliJExtService service = new IntelliJExtService(Paths.get(SERVER));
    assertEquals(TEST_VERSION, service.getVersion());
    String pid = service.getStatus().get("pid");
    assertEquals(TEST_VERSION, service.getVersion());
    assertEquals(pid, service.getStatus().get("pid"));
  }
}
