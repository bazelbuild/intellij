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
package com.google.idea.blaze.base.lang.buildfile.findusages;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.UsageGroupingRuleProviderImpl;
import com.intellij.usages.impl.rules.FileGroupingRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;

/**
 * This is a gross hack. We want to always include file paths for BUILD files in the 'find usages'
 * dialog.
 *
 * <p>This achieves that by overriding {@link UsageGroupingRuleProviderImpl}, replacing {@link
 * FileGroupingRule} with {@link BuildFileGroupingRule}.
 */
public class UsageGroupingRuleProviderOverride extends UsageGroupingRuleProviderImpl
    implements BaseComponent {

  @Override
  public void initComponent() {
    // remove UsageGroupingRuleProviderImpl from the list of EPs, effectively replacing it with
    // this class
    ExtensionPoint<UsageGroupingRuleProvider> ep =
        Extensions.getRootArea().getExtensionPoint(UsageGroupingRuleProvider.EP_NAME);
    ep.unregisterExtension(UsageGroupingRuleProviderImpl.class);
  }

  @Override
  public UsageGroupingRule[] getActiveRules(Project project, UsageViewSettings usageViewSettings) {
    UsageGroupingRule[] base = super.getActiveRules(project, usageViewSettings);
    for (int i = 0; i < base.length; i++) {
      if (base[i] instanceof FileGroupingRule) {
        base[i] = BuildFileGroupingRuleProvider.getGroupingRule(project);
      }
    }
    return base;
  }
}
