/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider;
import com.google.idea.blaze.android.projectsystem.BlazeToken;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** An implementation of {@link ApplicationProjectContextProvider} for the Blaze project system. */
public class BazelApplicationProjectContextProvider
    implements ApplicationProjectContextProvider, BlazeToken {

  private final Project project;

  public BazelApplicationProjectContextProvider(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public ApplicationProjectContext getApplicationProjectContextProvider(Client client) {
    ClientData clientData = client.getClientData();
    if (clientData == null) {
      return null;
    }
    String androidPackageName = clientData.getPackageName();
    if (androidPackageName == null) {
      return null;
    }
    return new BazelApplicationProjectContext(project, androidPackageName);
  }
}
