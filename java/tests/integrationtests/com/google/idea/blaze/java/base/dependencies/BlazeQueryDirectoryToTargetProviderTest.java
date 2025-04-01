package com.google.idea.blaze.java.base.dependencies;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.dependencies.BlazeQueryDirectoryToTargetProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.nio.file.Path;

import static com.google.common.truth.Truth.assertThat;

/** Tests for BlazeQueryDirectoryToTargetProvider */
@RunWith(JUnit4.class)
public class BlazeQueryDirectoryToTargetProviderTest extends BlazeIntegrationTestCase {
  private String worskpace = "workspace";
  private String dirToInclude = "dirToInclude";
  private String dirToExclude = "dirToExclude";

  @Override
  protected boolean isLightTestCase() {
    return false;
  }

  @Before
  public void doSetup() throws Throwable {
    fileSystem.createDirectory(worskpace);
    fileSystem.createDirectory(Path.of(worskpace, dirToInclude).toString());
    fileSystem.createDirectory(Path.of(worskpace, dirToExclude).toString());
  }

  @Test
  public void test() {
    WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File(fileSystem.getRootDir(), worskpace));
    WorkspacePath included = new WorkspacePath(dirToInclude);
    WorkspacePath excluded = new WorkspacePath(dirToExclude);
    WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(workspaceRoot);

    String queryString = BlazeQueryDirectoryToTargetProvider.getQueryString(
        ImportRoots.builder(workspaceRoot, BuildSystemName.Bazel)
            .add(DirectoryEntry.include(included))
            .add(DirectoryEntry.exclude(excluded))
            .build(),
        true,
        workspacePathResolver);


    assertThat(queryString).isEqualTo(TargetExpression.allFromPackageRecursive(included) + " - " + TargetExpression.allFromPackageRecursive(excluded));
  }
}
