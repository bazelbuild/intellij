package com.google.idea.testing.cidr;

import com.google.common.collect.ImmutableList;
import com.google.idea.sdkcompat.cidr.OCResolveConfigurationAdapter;
import com.intellij.openapi.project.Project;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Stub {@link OCResolveConfiguration} for testing. */
class StubOCResolveConfiguration extends OCResolveConfigurationAdapter {

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

  @Override
  public Set<VirtualFile> getPrecompiledHeaders() {
    return Collections.emptySet();
  }

  @Override
  public List<VirtualFile> getPrecompiledHeaders(OCLanguageKind kind, VirtualFile sourceFile) {
    return Collections.emptyList();
  }

  @Override
  public Collection<VirtualFile> getSources() {
    return Collections.emptyList();
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

  /* v172 */
  @Nullable
  @Override
  public String getPreprocessorDefines(OCLanguageKind kind, VirtualFile virtualFile) {
    return null;
  }
}
