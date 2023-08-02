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
package com.google.idea.blaze.base.qsync.settings;

import com.intellij.openapi.options.BoundSearchableConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.dsl.builder.BuilderKt;
import com.intellij.ui.dsl.builder.Cell;
import com.intellij.ui.dsl.builder.MutableProperty;
import com.intellij.ui.dsl.builder.Row;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import kotlin.Unit;

/** A configuration page for the settings dialog for query sync. */
public class QuerySyncConfigurable extends BoundSearchableConfigurable implements Configurable {
  private final QuerySyncSettings settings = QuerySyncSettings.getInstance();

  public QuerySyncConfigurable() {
    super(/* displayName= */ "Query Sync", /* helpTopic= */ "", /* _id= */ "query.sync");
  }

  @Override
  public DialogPanel createPanel() {
    return BuilderKt.panel(
        p -> {
          Row unusedDisplayDetailsOptionRow =
              p.row(
                  /* label= */ ((JLabel) null),
                  /* init= */ r -> {
                    Cell<JBCheckBox> unusedDisplayDetailsCheckBox =
                        r.checkBox("Display detailed dependency text in the editor")
                            .bind(
                                /* componentGet= */ AbstractButton::isSelected,
                                /* componentSet= */ (jbCheckBox, selected) -> {
                                  jbCheckBox.setSelected(selected);
                                  return Unit.INSTANCE;
                                },
                                /* prop= */ new MutableProperty<Boolean>() {
                                  @Override
                                  public Boolean get() {
                                    return settings.showDetailedInformationInEditor();
                                  }

                                  @Override
                                  public void set(Boolean selected) {
                                    settings.enableShowDetailedInformationInEditor(selected);
                                  }
                                });
                    return Unit.INSTANCE;
                  });
          if (!settings.enableSyncBeforeBuildByExperimentFile()) {
            Row unusedSyncBeforeBuildOptionRow =
                p.row(
                    /* label= */ ((JLabel) null),
                    /* init= */ r -> {
                      Cell<JBCheckBox> unusedSyncBeforeBuild =
                          r.checkBox("Sync automatically before building dependencies")
                              .bind(
                                  /* componentGet= */ AbstractButton::isSelected,
                                  /* componentSet= */ (jbCheckBox, selected) -> {
                                    jbCheckBox.setSelected(selected);
                                    return Unit.INSTANCE;
                                  },
                                  /* prop= */ new MutableProperty<Boolean>() {
                                    @Override
                                    public Boolean get() {
                                      return settings.syncBeforeBuild();
                                    }

                                    @Override
                                    public void set(Boolean selected) {
                                      settings.enableSyncBeforeBuild(selected);
                                    }
                                  });
                      return Unit.INSTANCE;
                    });
          }
          return Unit.INSTANCE;
        });
  }
}
