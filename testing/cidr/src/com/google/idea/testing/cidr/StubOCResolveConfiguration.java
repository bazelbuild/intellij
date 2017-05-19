package com.google.idea.testing.cidr;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceUtil;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerMacros;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeaderRoots;
import javax.annotation.Nullable;

/** Stub {@link OCResolveConfiguration} for testing. */
class StubOCResolveConfiguration extends UserDataHolderBase implements OCResolveConfiguration {

  private final Project project;
  private final HeaderRoots projectIncludeRoots;
  private final OCCompilerSettings compilerSettings;
  private final OCCompilerMacros compilerMacros;

  StubOCResolveConfiguration(Project project) {
    this.project = project;
    this.projectIncludeRoots = new HeaderRoots(ImmutableList.of());
    this.compilerMacros = new StubOCCompilerMacros();
    this.compilerSettings = new StubOCCompilerSettings(project);
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public String getDisplayName(boolean shorten) {
    return "Stub resolve configuration";
  }

  @Nullable
  @Override
  public VirtualFile getPrecompiledHeader() {
    return null;
  }

  @Nullable
  @Override
  public OCLanguageKind getDeclaredLanguageKind(VirtualFile sourceOrHeaderFile) {
    String fileName = sourceOrHeaderFile.getName();
    if (OCFileTypeHelpers.isSourceFile(fileName)) {
      return getLanguageKind(sourceOrHeaderFile);
    }
    return getMaximumLanguageKind();
  }

  private OCLanguageKind getLanguageKind(VirtualFile sourceFile) {
    OCLanguageKind kind = OCLanguageKindCalculator.tryFileTypeAndExtension(project, sourceFile);
    return kind != null ? kind : getMaximumLanguageKind();
  }

  @Override
  public OCLanguageKind getMaximumLanguageKind() {
    return OCLanguageKind.CPP;
  }

  @Override
  public OCLanguageKind getPrecompiledLanguageKind() {
    return getMaximumLanguageKind();
  }

  @Override
  public HeaderRoots getProjectHeadersRoots() {
    return projectIncludeRoots;
  }

  @Override
  public HeaderRoots getLibraryHeadersRoots(OCResolveRootAndConfiguration headerContext) {
    return projectIncludeRoots;
  }

  @Override
  public OCCompilerMacros getCompilerMacros() {
    return compilerMacros;
  }

  @Override
  public OCCompilerSettings getCompilerSettings() {
    return compilerSettings;
  }

  @Nullable
  @Override
  public Object getIndexingCluster() {
    return null;
  }

  @Override
  public int compareTo(OCResolveConfiguration o) {
    return OCWorkspaceUtil.compareConfigurations(this, o);
  }
}
