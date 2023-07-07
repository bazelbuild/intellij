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
package com.google.idea.blaze.base.qsync;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionContext;
import com.intellij.conversion.ConversionProcessor;
import com.intellij.conversion.ConverterProvider;
import com.intellij.conversion.ProjectConverter;
import com.intellij.conversion.WorkspaceSettings;
import com.intellij.openapi.util.NlsContexts;
import java.util.List;
import java.util.Optional;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A converter from aspect-sync projects into a query-sync ones. */
public class ProjectConverterProvider extends ConverterProvider {
  @Override
  @NlsContexts.DialogMessage
  @NotNull
  public String getConversionDescription() {
    return "The IDE and the project use incompatible sync modes.";
  }

  @Override
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new ProjectConverter() {

      @Override
      @Nullable
      public ConversionProcessor<WorkspaceSettings> createWorkspaceFileConverter() {
        return new ConversionProcessor<WorkspaceSettings>() {
          @Override
          public boolean isConversionNeeded(WorkspaceSettings workspaceSettings) {
            Optional<String> projectType = getProjectType(workspaceSettings);
            boolean isQuerySync = projectType.isPresent() && projectType.get().equals("QUERY_SYNC");
            // If the IDE setting does not match the project, needs conversion.
            return QuerySync.isEnabled() != isQuerySync;
          }

          @Override
          public void process(WorkspaceSettings workspaceSettings) throws CannotConvertException {
            // We have intentionally decided not to allow conversion from/to query-sync projects,
            // while this is doable it can mean deleting gigabytes of cached library data, and
            // cannot be easily undone. This can be an issue if someone is forced to go back
            // and is forced to a full legacy sync. We for now simply prevent these from being
            // opened.
            if (!QuerySync.isEnabled()) {
              throw new CannotConvertException(
                  "The project is created with a newer sync schema that is not supported "
                      + "with the current configuration of the IDE. In order to open this project "
                      + "switch the IDE to use query sync instead. See: go/query-sync.");
            } else {
              throw new CannotConvertException(
                  "The IDE is configured to use query sync, and is not compatible "
                      + "with projects created with the old sync. Please re-create the "
                      + "project on a new directory. More information: go/query-sync.");
            }
          }
        };
      }
    };
  }

  @NotNull
  private Optional<String> getProjectType(WorkspaceSettings workspaceSettings) {
    Element blazeSettings = workspaceSettings.getComponentElement("BlazeImportSettings");
    if (blazeSettings == null) {
      // This is not even a blaze project, definitely not a query sync one.
      return Optional.empty();
    }
    List<Element> options = blazeSettings.getChildren("option");
    Element projectType = null;
    for (Element option : options) {
      Attribute name = option.getAttribute("name");
      if (name != null && name.getValue().equals("projectType")) {
        projectType = option;
        break;
      }
    }
    if (projectType == null) {
      return Optional.empty();
    }
    Attribute value = projectType.getAttribute("value");
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(value.getValue());
  }
}
