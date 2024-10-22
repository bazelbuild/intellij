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
package com.google.idea.blaze.python.issueparser;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.issueparser.BlazeIssueParserProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.build.events.MessageEvent.Kind;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileManagerListener;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PyIssueParserProvider}. */
@RunWith(JUnit4.class)
public class PyIssueParserProviderTest extends BlazeTestCase {

  private static final BlazeImportSettings DUMMY_SETTINGS =
      new BlazeImportSettings("root", "", "", "", BuildSystemName.Bazel, ProjectType.ASPECT_SYNC);
  private static final WorkspaceRoot ROOT = WorkspaceRoot.fromImportSettings(DUMMY_SETTINGS);
  private ImmutableList<BlazeIssueParser.Parser> parsers;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettings importSettings =
        new BlazeImportSettings("root", "", "", "", BuildSystemName.Bazel, ProjectType.ASPECT_SYNC);
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(importSettings);

    ExtensionPointImpl<BlazeIssueParserProvider> ep =
        registerExtensionPoint(BlazeIssueParserProvider.EP_NAME, BlazeIssueParserProvider.class);
    ep.registerExtension(new PyIssueParserProvider());

    parsers = ImmutableList.copyOf(BlazeIssueParserProvider.getAllIssueParsers(project));

    registerExtensionPointByName("com.intellij.virtualFileManagerListener", VirtualFileManagerListener.class);
    applicationServices.register(VirtualFileManager.class, new VirtualFileManagerImpl(List.of(new CoreLocalFileSystem())));
  }

  @Test
  public void testParsePyTypeError() {
    BlazeIssueParser blazeIssueParser = new BlazeIssueParser(parsers);
    IssueOutput issue =
        blazeIssueParser.parseIssue(
            "File \"dataset.py\", line 109, in Dataset: "
                + "Name 'function' is not defined [name-error]");
    assertThat(issue).isNotNull();
    assertThat(issue.getKind()).isEqualTo(Kind.ERROR);
  }
}
