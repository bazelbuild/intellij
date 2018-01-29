/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.attach;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.ndk.run.attach.AndroidNativeAttachConfiguration;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.idea.blaze.android.sync.projectstructure.AndroidFacetModuleCustomizer;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * {@link AndroidNativeAttachConfiguration} that skips a bunch of checks that are irrelevant to
 * blaze projects so that {@link #validate} passes.
 */
public class BlazeAndroidNativeAttachConfiguration extends AndroidNativeAttachConfiguration {
  BlazeAndroidNativeAttachConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory);
  }

  /**
   * TODO: check if we really need to use {@link AndroidProject#PROJECT_TYPE_LIBRARY} instead of
   * {@link AndroidProject#PROJECT_TYPE_APP} in {@link
   * AndroidFacetModuleCustomizer#configureFacet(AndroidFacet)}.
   *
   * <p>See {@link AndroidRunConfigurationBase#validate}.
   */
  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(AndroidFacet androidFacet) {
    return Pair.create(Boolean.TRUE, null);
  }

  /**
   * TODO: fix upstream to work with non-debuggable apps on debuggable devices.
   *
   * <p>See {@link NativeAndroidDebuggerState#validate}.
   */
  @Override
  public List<ValidationError> validate(@Nullable Executor executor) {
    return super.validate(executor)
        .stream()
        .filter(e -> !e.getMessage().equals("Application is not debuggable"))
        .collect(Collectors.toList());
  }
}
