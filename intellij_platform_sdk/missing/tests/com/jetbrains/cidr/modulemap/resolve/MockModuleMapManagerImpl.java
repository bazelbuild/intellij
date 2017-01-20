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
package com.jetbrains.cidr.modulemap.resolve;

import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.modulemap.ModuleMapModules;
import java.util.Collections;
import java.util.List;

/**
 * Dummy class that integration tests depend on, but missing from Android Studio 2.3.
 *
 * <p>Android Studio 2.3 added a ModuleMapManager projectService with MockModuleMapManagerImpl as
 * testServiceImplementation to CidrLangPlugin.xml. We don't use this service at all, but since
 * Android Studio releases don't contain test classes, trying to load the cidr-lang plugin during
 * integration tests will fail due to the missing test service implementation.
 */
public class MockModuleMapManagerImpl extends ModuleMapManager {
  @Override
  public ModuleMapModules getModules(OCResolveConfiguration ocResolveConfiguration) {
    return ModuleMapModules.Companion.getEMPTY();
  }

  @Override
  public List<HeadersSearchRoot> getHeaderSearchRoots(
      OCResolveConfiguration ocResolveConfiguration) {
    return Collections.emptyList();
  }
}
