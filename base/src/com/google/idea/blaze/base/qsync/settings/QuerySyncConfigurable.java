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

import com.google.common.base.Suppliers;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.ExperimentValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.BoundSearchableConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.dsl.builder.BuilderKt;
import com.intellij.ui.dsl.builder.ButtonKt;
import com.intellij.ui.dsl.builder.Cell;
import com.intellij.ui.dsl.builder.HyperlinkEventAction;
import com.intellij.ui.dsl.builder.MutableProperty;
import com.intellij.ui.dsl.builder.Row;
import com.intellij.ui.dsl.builder.RowsRange;
import com.intellij.ui.dsl.builder.UtilsKt;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import kotlin.Unit;

/** A configuration page for the settings dialog for query sync. */
public class QuerySyncConfigurable extends BoundSearchableConfigurable
    implements Configurable.Beta {

  // If enabled query Sync via a legacy way (set up experimental value).
  // Only read the initial value, as the sync mode should not change over a single run of the IDE.
  private static final Supplier<Boolean> QUERY_SYNC_ENABLED_LEGACY =
      Suppliers.memoize(
          () ->
              getFirstExperimentValueWithoutId(
                  "use.query.sync", QuerySyncSettingsExperimentLoader.ID, false));

  // If enabled sync before build for Query Sync via a legacy way (set up experimental value)
  private static final Supplier<Boolean> SYNC_BEFORE_BUILD_ENABLED_LEGACY =
      Suppliers.memoize(
          () ->
              getFirstExperimentValueWithoutId(
                  "query.sync.before.build", QuerySyncSettingsExperimentLoader.ID, false));
  private final QuerySyncSettings settings = QuerySyncSettings.getInstance();

  // Makes sure we have checked query sync enable option before any changes. Otherwise, it may
  // memorize the changed value.
  private final boolean useQuerySync = QuerySync.isEnabled();

  // Provides access to enableQuerySyncCheckBoxCell for other rows
  private Cell<JBCheckBox> enableQuerySyncCheckBoxCell = null;

  public QuerySyncConfigurable() {
    super(/* displayName= */ "Query Sync", /* helpTopic= */ "", /* _id= */ "query.sync");
  }

  private void popupRestartDialogue() {
    int result =
        Messages.showYesNoDialog(
            "Restart "
                + ApplicationNamesInfo.getInstance().getFullProductName()
                + " for the changes to take effect",
            "Restart Required",
            ApplicationManager.getApplication().isRestartCapable() ? "Restart" : "Shutdown",
            "Not Now",
            Messages.getQuestionIcon());
    if (result == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  @Override
  public void apply() {
    super.apply();
    if (useQuerySync != settings.useQuerySync()) {
      popupRestartDialogue();
    }
  }

  private static boolean getFirstExperimentValueWithoutId(
      String key, String id, boolean defaultValue) {
    for (ExperimentValue experimentValue : ExperimentService.getInstance().getOverrides(key)) {
      if (!experimentValue.id().equals(id)) {
        return experimentValue.value().equals("1");
      }
    }
    return defaultValue;
  }

  @Override
  public DialogPanel createPanel() {
    return BuilderKt.panel(
        p -> {
          boolean enabledByExperimentFile = QUERY_SYNC_ENABLED_LEGACY.get();
          // Enable query sync checkbox
          Cell<JEditorPane> unusedEnableQuerySyncRow =
              p.row(
                      /* label= */ ((JLabel) null),
                      /* init= */ r -> {
                        enableQuerySyncCheckBoxCell =
                            r.checkBox("Enable Query Sync")
                                .enabled(!enabledByExperimentFile)
                                .bind(
                                    /* componentGet= */ AbstractButton::isSelected,
                                    /* componentSet= */ (jbCheckBox, selected) -> {
                                      jbCheckBox.setSelected(selected);
                                      return Unit.INSTANCE;
                                    },
                                    /* prop= */ new MutableProperty<Boolean>() {
                                      @Override
                                      public Boolean get() {
                                        return settings.useQuerySync() || enabledByExperimentFile;
                                      }

                                      @Override
                                      public void set(Boolean selected) {
                                        settings.enableUseQuerySync(selected);
                                      }
                                    })
                                .comment(
                                    enabledByExperimentFile
                                        ? "query sync is forcefully enabled by the old flag from"
                                            + " the .intellij-experiments file. "
                                        : "",
                                    UtilsKt.DEFAULT_COMMENT_WIDTH,
                                    HyperlinkEventAction.HTML_HYPERLINK_INSTANCE);
                        return Unit.INSTANCE;
                      })
                  .comment(
                      enabledByExperimentFile ? "" : "Requires restart",
                      UtilsKt.DEFAULT_COMMENT_WIDTH,
                      HyperlinkEventAction.HTML_HYPERLINK_INSTANCE);

          // Other sub options
          RowsRange unusedRowRange =
              p.indent(
                  ip -> {
                    Row unusedDisplayDetailsOptionRow =
                        ip.row(
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
                                              settings.enableShowDetailedInformationInEditor(
                                                  selected);
                                            }
                                          })
                                      .enabledIf(ButtonKt.getSelected(enableQuerySyncCheckBoxCell));
                              return Unit.INSTANCE;
                            });
                    Row unusedSyncBeforeBuildOptionRow =
                        ip.row(
                            /* label= */ ((JLabel) null),
                            /* init= */ r -> {
                              Cell<JBCheckBox> syncBeforeBuildCheckBox =
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
                                              return settings.syncBeforeBuild()
                                                  || SYNC_BEFORE_BUILD_ENABLED_LEGACY.get();
                                            }

                                            @Override
                                            public void set(Boolean selected) {
                                              settings.enableSyncBeforeBuild(selected);
                                            }
                                          });
                              if (!SYNC_BEFORE_BUILD_ENABLED_LEGACY.get()) {
                                syncBeforeBuildCheckBox =
                                    syncBeforeBuildCheckBox.enabledIf(
                                        ButtonKt.getSelected(enableQuerySyncCheckBoxCell));
                              } else {
                                syncBeforeBuildCheckBox =
                                    syncBeforeBuildCheckBox
                                        .enabled(false)
                                        .comment(
                                            "This feature is forcefully enabled by the old flag"
                                                + " from the .intellij-experiments file. ",
                                            UtilsKt.DEFAULT_COMMENT_WIDTH,
                                            HyperlinkEventAction.HTML_HYPERLINK_INSTANCE);
                              }
                              return Unit.INSTANCE;
                            });
                    return Unit.INSTANCE;
                  });
          return Unit.INSTANCE;
        });
  }
}
