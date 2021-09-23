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
package com.google.idea.blaze.base.console;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;

/** Prints text to the blaze console. */
public interface BlazeConsoleService {

  static BlazeConsoleService getInstance(Project project) {
    return ServiceManager.getService(project, BlazeConsoleService.class);
  }

  void print(String text, ConsoleViewContentType contentType);

  void printHyperlink(String hyperlinkText, @Nullable HyperlinkInfo info);

  void clear();

  void setCustomFilters(List<Filter> filters);

  void setStopHandler(@Nullable Runnable runnable);

  void activateConsoleWindow();
}
