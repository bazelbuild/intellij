package com.google.idea.testing.cidr;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.toolchains.DefaultCidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import java.io.File;
import javax.annotation.Nullable;

/** Stub {@link OCCompilerSettings} for testing. */
class StubOCCompilerSettings extends OCCompilerSettings {

  private final Project project;

  StubOCCompilerSettings(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public OCCompilerKind getCompiler(OCLanguageKind languageKind) {
    return OCCompilerKind.CLANG;
  }

  @Nullable
  @Override
  public File getCompilerExecutable(OCLanguageKind languageKind) {
    return null;
  }

  @Override
  public File getCompilerWorkingDir() {
    return VfsUtilCore.virtualToIoFile(project.getBaseDir());
  }

  @Override
  public CidrToolEnvironment getEnvironment() {
    return new DefaultCidrToolEnvironment();
  }

  @Override
  public CidrCompilerSwitches getCompilerSwitches(
      OCLanguageKind languageKind, @Nullable VirtualFile sourceFile) {
    return new CidrSwitchBuilder().build();
  }
}
