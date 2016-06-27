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
package com.google.idea.blaze.base.projectview.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.Sections;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Parses and writes project views.
 */
public class ProjectViewParser {

  private final BlazeContext context;
  private final WorkspacePathResolver workspacePathResolver;
  private final boolean recursive;

  ImmutableList.Builder<ProjectViewSet.ProjectViewFile> projectViewFiles = ImmutableList.builder();

  public ProjectViewParser(BlazeContext context,
                           WorkspacePathResolver workspacePathResolver) {
    this.context = context;
    this.workspacePathResolver = workspacePathResolver;
    this.recursive = true;
  }

  public void parseProjectView(File projectViewFile) {
    String projectViewText = null;
    try {
      projectViewText = ProjectViewStorageManager.getInstance().loadProjectView(projectViewFile);
    }
    catch (IOException e) {
      // Error handled below
    }
    if (projectViewText == null) {
      IssueOutput.error(String.format("Could not load project view file: '%s'", projectViewFile.getPath()))
        .submit(context);
      return;
    }
    parseProjectView(new ParseContext(context, workspacePathResolver, projectViewFile, projectViewText));
  }

  public void parseProjectView(String text) {
    parseProjectView(new ParseContext(context, workspacePathResolver, null, text));
  }

  private void parseProjectView(ParseContext parseContext) {
    Map<SectionKey, Section> sectionMap = Maps.newHashMap();

    while (!parseContext.atEnd()) {
      if (parseContext.current().indent != 0) {
        parseContext.addError(String.format("Invalid indentation on line: '%s'", parseContext.current().text));
        skipSection(parseContext);
        continue;
      }
      Section section = null;
      SectionParser producingParser = null;
      for (SectionParser sectionParser : Sections.getParsers()) {
        section = sectionParser.parse(this, parseContext);
        if (section != null) {
          producingParser = sectionParser;
          break;
        }
      }
      if (section != null) {
        SectionKey key = producingParser.getSectionKey();
        if (!sectionMap.containsKey(key)) {
          sectionMap.put(key, section);
        } else {
          BlazeContext context = parseContext.getContext();
          IssueOutput.error(String.format("Duplicate attribute: '%s'", producingParser.getName()))
            .inFile(parseContext.getProjectViewFile())
            .submit(context);
        }
      } else {
        parseContext.addError(String.format("Could not parse: '%s'", parseContext.current().text));
        parseContext.consume();

        // Skip past the entire section
        skipSection(parseContext);
      }
    }

    ProjectView.Builder builder = ProjectView.builder();
    for (Map.Entry<SectionKey, Section> entry : sectionMap.entrySet()) {
      builder.put(entry.getKey(), entry.getValue());
    }

    projectViewFiles.add(new ProjectViewSet.ProjectViewFile(builder.build(), parseContext.getProjectViewFile()));
  }

  /**
   * Skips all lines until the next unindented, non-empty line.
   */
  private static void skipSection(ParseContext parseContext) {
    while (!parseContext.atEnd() && parseContext.current().indent != 0) {
      parseContext.consume();
    }
  }

  public boolean isRecursive() {
    return recursive;
  }

  public ProjectViewSet getResult() {
    return new ProjectViewSet(projectViewFiles.build());
  }

  public static String projectViewToString(ProjectView projectView) {
    StringBuilder sb = new StringBuilder();
    int sectionCount = 0;
    for (SectionParser sectionParser : Sections.getParsers()) {
      SectionKey sectionKey = sectionParser.getSectionKey();
      Section section = projectView.getSectionOfType(sectionKey);
      if (section != null) {
        if (sectionCount > 0) {
          sb.append('\n');
        }
        sectionParser.print(sb, section);
        ++sectionCount;
      }
    }
    return sb.toString();
  }
}
