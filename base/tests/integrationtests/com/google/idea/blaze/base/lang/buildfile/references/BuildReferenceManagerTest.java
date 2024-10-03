package com.google.idea.blaze.base.lang.buildfile.references;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.completion.BuildLookupElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class BuildReferenceManagerTest extends BuildFileIntegrationTestCase {
  private MockProjectViewManager projectViewManager;

  @Test
  public void testResolvePackageLookupElementsIgnoresIgnoredDirectories() {
    projectViewManager = new MockProjectViewManager(getProject());
    setProjectView("directories:", "  .", "derive_targets_from_directories: true");

    workspace.createDirectory(new WorkspacePath(".ijwb"));
    workspace.createDirectory(new WorkspacePath("src"));
    workspace.createDirectory(new WorkspacePath("src/go"));
    workspace.createFile(new WorkspacePath("src/go/BUILD.bazel"), "");
    workspace.createFile(new WorkspacePath("src/go/main.go"), "package main\n");
    BuildFile topTelevelBuildFile = createBuildFile(new WorkspacePath("BUILD.bazel"), "");

    BuildReferenceManager manager = new BuildReferenceManager(getProject());
    FileLookupData nonLocalLookupData =
        FileLookupData.nonLocalFileLookup(
            "//", topTelevelBuildFile, QuoteType.NoQuotes, FileLookupData.PathFormat.NonLocal);
    assertThat(nonLocalLookupData).isNotNull();
    List<String> results =
        Arrays.stream(manager.resolvePackageLookupElements(nonLocalLookupData))
            .map(BuildLookupElement::getLookupString)
            .toList();
    assertThat(results).containsAtLeastElementsIn(new String[] {"//src/go"});
    assertThat(results).containsNoneIn(new String[] {".ijwb"});

    String originalLabel = "//:";
    Label packageLabel = topTelevelBuildFile.getPackageLabel();
    assertThat(packageLabel).isNotNull();
    String basePackagePath = packageLabel.blazePackage().relativePath();
    String filePath = basePackagePath + "/" + LabelUtils.getRuleComponent(originalLabel);
    FileLookupData localLookupData =
        new FileLookupData(
            originalLabel,
            topTelevelBuildFile,
            basePackagePath,
            filePath,
            FileLookupData.PathFormat.PackageLocal,
            QuoteType.NoQuotes,
            null);
    List<String> otherResults =
        Arrays.stream(manager.resolvePackageLookupElements(localLookupData))
            .map(BuildLookupElement::getLookupString)
            .toList();
    assertThat(otherResults).containsAtLeastElementsIn(new String[] {"//:src"});
    assertThat(otherResults).containsNoneIn(new String[] {":.ijwb"});
  }

  protected void setProjectView(String... contents) {
    BlazeContext context = BlazeContext.create();
    ProjectViewParser projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));

    ProjectViewSet result = projectViewParser.getResult();
    assertThat(result.getProjectViewFiles()).isNotEmpty();
    projectViewManager.setProjectView(result);
  }
}
