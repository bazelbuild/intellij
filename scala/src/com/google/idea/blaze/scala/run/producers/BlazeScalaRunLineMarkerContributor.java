/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.scala.run.producers;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.jetbrains.plugins.scala.runner.ScalaRunLineMarkerContributor;

/** Replaces the icon for {@link ScalaRunLineMarkerContributor} to match other blaze plugins. */
public class BlazeScalaRunLineMarkerContributor extends RunLineMarkerContributor {
  @Nullable
  @Override
  public Info getInfo(PsiElement element) {
    Info info = new ScalaRunLineMarkerContributor().getInfo(element);
    if (info == null) {
      return null;
    }
    return new ReplacementInfo(info, AllIcons.RunConfigurations.TestState.Run);
  }

  private static class ReplacementInfo extends Info {
    ReplacementInfo(Info info, Icon icon) {
      super(icon, info.actions, info.tooltipProvider);
    }

    @Override
    public boolean shouldReplace(Info other) {
      return Arrays.equals(actions, other.actions);
    }
  }
}
