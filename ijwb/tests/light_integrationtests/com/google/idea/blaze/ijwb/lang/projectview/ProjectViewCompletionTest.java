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
package com.google.idea.blaze.ijwb.lang.projectview;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.EditorTestHelper;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.intellij.psi.PsiFile;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Project view completion tests requiring IJwB's sync plugins to be present. */
@RunWith(JUnit4.class)
public class ProjectViewCompletionTest extends BlazeIntegrationTestCase {

  protected EditorTestHelper editorTest;

  @Before
  public final void doSetup() {
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    editorTest = new EditorTestHelper(getProject(), testFixture);
  }

  private PsiFile setInput(String... fileContents) {
    return testFixture.configureByText(".blazeproject", Joiner.on("\n").join(fileContents));
  }

  @Test
  public void testAdditionalLanguagesCompletion() {
    setInput("additional_languages:", "  <caret>");

    String[] types = editorTest.getCompletionItemsAsStrings();

    assertThat(types)
        .asList()
        .containsAtLeastElementsIn(
            LanguageSupport.availableAdditionalLanguages(WorkspaceType.JAVA)
                .stream()
                .map(LanguageClass::getName)
                .collect(Collectors.toList()));
  }
}
