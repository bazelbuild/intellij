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
package com.google.idea.sdkcompat.vcs;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.spellchecker.ui.SpellCheckingEditorCustomization;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.RightMarginEditorCustomization;
import com.intellij.ui.WrapWhenTypingReachesRightMarginCustomization;
import java.util.List;

/** Handles changelist editor customizations that differ between SDK versions. */
public class ChangeListEditorCompatUtils {

  public static List<EditorCustomization> getDescriptionEditorCustomizations(
      Project project, VcsConfiguration configuration) {
    return ImmutableList.of(
        SpellCheckingEditorCustomization.getInstance(configuration.CHECK_COMMIT_MESSAGE_SPELLING),
        new RightMarginEditorCustomization(
            configuration.USE_COMMIT_MESSAGE_MARGIN, configuration.COMMIT_MESSAGE_MARGIN_SIZE),
        WrapWhenTypingReachesRightMarginCustomization.getInstance(
            configuration.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN));
  }
}
