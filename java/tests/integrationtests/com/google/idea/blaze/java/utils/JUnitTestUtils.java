package com.google.idea.blaze.java.utils;

import com.google.idea.blaze.base.TestFileSystem;
import com.google.idea.blaze.base.WorkspaceFileSystem;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags.JUnitVersion;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.stream.Stream;

public final class JUnitTestUtils {
  public static JUnitVersion[] JUNIT_VERSIONS_UNDER_TEST = new JUnitVersion[]{
    JUnitVersion.JUNIT_4, JUnitVersion.JUNIT_5
  };

  public static void setupForJUnitTests(WorkspaceFileSystem workspace, TestFileSystem fileSystem) {
    // required for IntelliJ to recognize annotations, JUnit version, etc.
    // JUnit3
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runner/RunWith.java"),
        "package org.junit.runner;"
            + "public @interface RunWith {"
            + "    Class<? extends Runner> value();"
            + "}");
    // JUnit4
    workspace.createPsiFile(
        new WorkspacePath("org/junit/Test.java"),
        "package org.junit;",
        "public @interface Test {}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runners/JUnit4.java"),
        "package org.junit.runners;",
        "public class JUnit4 {}");
    // JUnit5
    // We use `fileSystem` instead of `workspace` because the JUnit5 detector from IntelliJ
    // relies on file structure to detect packages.
    // If we used the workspace, the packages would include the workspace root, and would be named
    // workspace.org.junit.jupiter...., and the test harness wouldn't be able to find them.
    fileSystem.createFile(
        "org/junit/jupiter/api/Test.java",
        "package org.junit.jupiter.api;",
        "public @interface Test {}");
  }

  public static PsiFile createGenericJUnitFile(WorkspaceFileSystem workspace, JUnitVersion jUnitVersion) {
    return createGenericJUnitFile(workspace, jUnitVersion, "TestClass");
  }

  public static PsiFile createGenericJUnitFile(WorkspaceFileSystem workspace, JUnitVersion jUnitVersion, String className) {
    WorkspacePath wsPath = new WorkspacePath(String.format("java/com/google/test/%s.java", className));
    switch (jUnitVersion) {
      case JUNIT_4:
        return workspace.createPsiFile(
            wsPath,
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
            String.format("public class %s {", className),
            "  @org.junit.Test",
            "  public void testMethod1() {}",
            "  @org.junit.Test",
            "  public void testMethod2() {}",
            "}");
      case JUNIT_5:
        return workspace.createPsiFile(
            wsPath,
            "package com.google.test;",
            // We need to use the `Testable` annotation so that the tests can see the class.
            // This is only necessary for testing because we don't have JUnit's class discovery mechanism.
            // Ref: https://junit.org/junit5/docs/5.0.0/api/org/junit/platform/commons/annotation/Testable.html"
            "@org.junit.platform.commons.annotation.Testable",
            String.format("public class %s {", className),
            "  @org.junit.jupiter.api.Test",
            "  public void testMethod1() {}",
            "  @org.junit.jupiter.api.Test",
            "  public void testMethod2() {}",
            "}");
      default:
        throw new RuntimeException("Unsupported JUnit Version under test: " + jUnitVersion.toString());
    }
  }
}
