/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.toolwindow;

import com.google.idea.blaze.base.console.NonProblemFilterWrapper;
import com.google.idea.blaze.base.run.filter.BlazeTargetFilter;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput.OutputType;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.intellij.codeEditor.printing.PrintAction;
import com.intellij.execution.actions.ClearConsoleAction;
import com.intellij.execution.filters.ConsoleDependentFilterProvider;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.ConsoleFilterProviderEx;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.LayoutFocusTraversalPolicy;

/**
 * Simplified copy and replacement of {@link com.google.idea.blaze.base.console.BlazeConsoleView},
 * (which can be deleted after the new tool-window is released).
 *
 * <p>The class handles how the output of a single task is displayed in the tool-window.
 */
final class ConsoleView implements Disposable {

  /** The counter that is used to create IntelliJ UI components ids. */
  private static long consoleIdCounter;

  private static final Class<?>[] IGNORED_CONSOLE_ACTION_TYPES = {
    PreviousOccurenceToolbarAction.class,
    NextOccurenceToolbarAction.class,
    ClearConsoleAction.class,
    PrintAction.class
  };

  private final Project project;
  private final ConsoleViewImpl consoleView;
  private final JComponent content;
  private final CompositeFilter customFilters = new CompositeFilter();

  private final AnsiEscapeDecoder ansiEscapeDecoder = new AnsiEscapeDecoder();

  static ConsoleView create(Project project) {
    ConsoleViewImpl consoleView =
        new ConsoleViewImpl(
            project,
            GlobalSearchScope.allScope(project),
            /* viewer= */ false,
            /* usePredefinedFilters= */ false);

    return new ConsoleView(project, consoleView, createContent(project, consoleView));
  }

  private ConsoleView(Project project, ConsoleViewImpl consoleView, JComponent content) {
    this.project = project;
    this.consoleView = consoleView;
    this.content = content;

    consoleView.addMessageFilter(customFilters);
    addWrappedPredefinedFilters();
    // add target filter last, so it doesn't override other links containing a target string
    consoleView.addMessageFilter(new BlazeTargetFilter(false));
    Disposer.register(this, consoleView);
  }

  void setCustomFilters(List<Filter> filters) {
    customFilters.setCustomFilters(filters);
  }

  void navigateToHyperlink(HyperlinkInfo link, int originalOffset) {
    RangeHighlighter range = findLinkRange(link, originalOffset);
    if (range != null) {
      consoleView.scrollTo(range.getStartOffset());
    }
  }

  @Nullable
  private RangeHighlighter findLinkRange(HyperlinkInfo link, int originalOffset) {
    // first check if it's still at the same offset
    Document doc = consoleView.getEditor().getDocument();
    if (doc.getTextLength() <= originalOffset) {
      return null;
    }
    int lineNumber = doc.getLineNumber(originalOffset);
    EditorHyperlinkSupport helper = consoleView.getHyperlinks();
    for (RangeHighlighter range : helper.findAllHyperlinksOnLine(lineNumber)) {
      if (Objects.equals(EditorHyperlinkSupport.getHyperlinkInfo(range), link)) {
        return range;
      }
    }
    // fall back to searching all hyperlinks
    return findRangeForHyperlink(link);
  }

  @Nullable
  private RangeHighlighter findRangeForHyperlink(HyperlinkInfo link) {
    Map<RangeHighlighter, HyperlinkInfo> links = consoleView.getHyperlinks().getHyperlinks();
    for (Map.Entry<RangeHighlighter, HyperlinkInfo> entry : links.entrySet()) {
      if (Objects.equals(link, entry.getValue())) {
        return entry.getKey();
      }
    }
    return null;
  }

  private static boolean shouldIgnoreAction(AnAction action) {
    for (Class<?> actionType : IGNORED_CONSOLE_ACTION_TYPES) {
      if (actionType.isInstance(action)) {
        return true;
      }
    }
    return false;
  }

  private static final String TOOLBAR_ACTION_PLACE = "OutputView.Toolbar";

  private static JComponent createContent(Project project, ConsoleViewImpl consoleView) {
    // Create runner UI layout
    RunnerLayoutUi.Factory factory = RunnerLayoutUi.Factory.getInstance(project);
    RunnerLayoutUi layoutUi = factory.create("", "", "session", project);
    layoutUi.getOptions().setMoveToGridActionEnabled(false).setMinimizeActionEnabled(false);

    Content console =
        layoutUi.createContent(nextContentId(), consoleView.getComponent(), "", null, null);
    console.setCloseable(false);
    layoutUi.addContent(console, 0, PlaceInGrid.right, false);

    // Adding actions
    DefaultActionGroup group = new DefaultActionGroup();
    layoutUi.getOptions().setLeftToolbar(group, TOOLBAR_ACTION_PLACE);

    // Initializing prev and next occurrences actions
    OccurenceNavigator navigator = fromConsoleView(consoleView);
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    AnAction prevAction = actionsManager.createPrevOccurenceAction(navigator);
    prevAction.getTemplatePresentation().setText(navigator.getPreviousOccurenceActionName());
    AnAction nextAction = actionsManager.createNextOccurenceAction(navigator);
    nextAction.getTemplatePresentation().setText(navigator.getNextOccurenceActionName());

    group.addAll(prevAction, nextAction);

    AnAction[] consoleActions = consoleView.createConsoleActions();
    for (AnAction action : consoleActions) {
      if (!shouldIgnoreAction(action)) {
        group.add(action);
      }
    }

    JComponent layoutComponent = layoutUi.getComponent();
    layoutComponent.setFocusTraversalPolicyProvider(true);
    layoutComponent.setFocusTraversalPolicy(
        new LayoutFocusTraversalPolicy() {
          @Override
          public Component getDefaultComponent(Container container) {
            if (container.equals(layoutComponent)) {
              return consoleView.getPreferredFocusableComponent();
            }
            return super.getDefaultComponent(container);
          }
        });

    return layoutComponent;
  }

  private static String nextContentId() {
    return "BuildTaskConsole" + consoleIdCounter++;
  }

  public void clear() {
    consoleView.clear();
  }

  void println(StatusOutput output) {
    println(output.getStatus(), OutputType.NORMAL);
  }

  void println(PrintOutput output) {
    println(output.getText(), output.getOutputType());
  }

  private void println(String text, OutputType outputType) {
    ansiEscapeDecoder.escapeText(
        text,
        outputType == OutputType.ERROR ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT,
        (t, k) -> consoleView.print(t, ConsoleViewContentType.getConsoleViewType(k)));
    consoleView.print(
        "\n",
        outputType == OutputType.ERROR
            ? ConsoleViewContentType.ERROR_OUTPUT
            : ConsoleViewContentType.NORMAL_OUTPUT);
  }

  public void printHyperlink(String text, HyperlinkInfo hyperlinkInfo) {
    consoleView.printHyperlink(text, hyperlinkInfo);
  }

  @Override
  public void dispose() {}

  /** A composite filter composed of a modifiable list of custom filters. */
  private static class CompositeFilter implements Filter {
    private final List<Filter> customFilters = new ArrayList<>();

    void setCustomFilters(List<Filter> filters) {
      customFilters.clear();
      customFilters.addAll(filters);
    }

    @Nullable
    @Override
    public Result applyFilter(String line, int entireLength) {
      return customFilters
          .stream()
          .map(f -> f.applyFilter(line, entireLength))
          .filter(Objects::nonNull)
          .reduce(this::combine)
          .orElse(null);
    }

    Result combine(Result first, Result second) {
      List<ResultItem> items = new ArrayList<>();
      items.addAll(first.getResultItems());
      items.addAll(second.getResultItems());
      return new Result(items);
    }
  }

  /** Add the global filters, wrapped to separate them from blaze problems. */
  private void addWrappedPredefinedFilters() {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    for (ConsoleFilterProvider provider : ConsoleFilterProvider.FILTER_PROVIDERS.getExtensions()) {
      Arrays.stream(getFilters(scope, provider))
          .forEach(f -> consoleView.addMessageFilter(NonProblemFilterWrapper.wrap(f)));
    }
  }

  private Filter[] getFilters(GlobalSearchScope scope, ConsoleFilterProvider provider) {
    if (provider instanceof ConsoleDependentFilterProvider) {
      return ((ConsoleDependentFilterProvider) provider)
          .getDefaultFilters(consoleView, project, scope);
    }
    if (provider instanceof ConsoleFilterProviderEx) {
      return ((ConsoleFilterProviderEx) provider).getDefaultFilters(project, scope);
    }
    return provider.getDefaultFilters(project);
  }

  /** Changes the action text to reference 'problems', not 'stack traces'. */
  private static OccurenceNavigator fromConsoleView(ConsoleViewImpl console) {
    return new OccurenceNavigator() {
      @Override
      public boolean hasNextOccurence() {
        return console.hasNextOccurence();
      }

      @Override
      public boolean hasPreviousOccurence() {
        return console.hasPreviousOccurence();
      }

      @Nullable
      @Override
      public OccurenceInfo goNextOccurence() {
        return console.goNextOccurence();
      }

      @Nullable
      @Override
      public OccurenceInfo goPreviousOccurence() {
        return console.goPreviousOccurence();
      }

      @Override
      public String getNextOccurenceActionName() {
        return "Next Problem";
      }

      @Override
      public String getPreviousOccurenceActionName() {
        return "Previous Problem";
      }
    };
  }

  public JComponent getContent() {
    return content;
  }
}
