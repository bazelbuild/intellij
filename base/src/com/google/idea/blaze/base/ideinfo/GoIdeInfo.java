/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Objects;
import javax.annotation.Nullable;

/** Ide info specific to go rules. */
public final class GoIdeInfo implements ProtoWrapper<IntellijIdeInfo.GoIdeInfo> {
  private final ImmutableList<ArtifactLocation> sources;
  @Nullable private final String importPath;
  @Nullable private final Label libraryLabel; // only valid for tests

  private GoIdeInfo(
      ImmutableList<ArtifactLocation> sources,
      @Nullable String importPath,
      @Nullable Label libraryLabel) {
    this.sources = sources;
    this.importPath = importPath;
    this.libraryLabel = libraryLabel;
  }

  public static GoIdeInfo fromProto(
      IntellijIdeInfo.GoIdeInfo proto, Label targetLabel, Kind targetKind) {
    return new GoIdeInfo(
        ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto),
        fixImportPath(Strings.emptyToNull(proto.getImportPath()), targetLabel, targetKind),
        extractLibraryLabel(Strings.emptyToNull(proto.getLibraryLabel()), targetLabel, targetKind));
  }

  @Nullable
  private static String fixImportPath(
      @Nullable String oldImportPath, Label targetLabel, Kind targetKind) {
    for (ImportPathReplacer fixer : ImportPathReplacer.EP_NAME.getExtensions()) {
      if (fixer.shouldReplace(oldImportPath)) {
        return fixer.getReplacement(targetLabel, targetKind);
      }
    }
    return oldImportPath;
  }

  @Nullable
  private static Label extractLibraryLabel(
      @Nullable String libraryLabelString, Label targetLabel, Kind targetKind) {
    if (libraryLabelString == null || !targetKind.isOneOf(Kind.GO_TEST, Kind.GO_APPENGINE_TEST)) {
      return null;
    }
    Label libraryLabel = Label.create(libraryLabelString);
    return !libraryLabel.equals(targetLabel) ? libraryLabel : null;
  }

  @Override
  public IntellijIdeInfo.GoIdeInfo toProto() {
    IntellijIdeInfo.GoIdeInfo.Builder builder =
        IntellijIdeInfo.GoIdeInfo.newBuilder().addAllSources(ProtoWrapper.mapToProtos(sources));
    ProtoWrapper.setIfNotNull(builder::setImportPath, importPath);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setLibraryLabel, libraryLabel);
    return builder.build();
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  @Nullable
  public String getImportPath() {
    return importPath;
  }

  @Nullable
  public Label getLibraryLabel() {
    return libraryLabel;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for go rule info */
  public static class Builder {
    private final ImmutableList.Builder<ArtifactLocation> sources = ImmutableList.builder();
    @Nullable private String importPath = null;

    public Builder addSources(Iterable<ArtifactLocation> sources) {
      this.sources.addAll(sources);
      return this;
    }

    public Builder setImportPath(String importPath) {
      this.importPath = importPath;
      return this;
    }

    public GoIdeInfo build() {
      return new GoIdeInfo(sources.build(), importPath, null);
    }
  }

  @Override
  public String toString() {
    return "GoIdeInfo{"
        + "\n"
        + "  sources="
        + getSources()
        + "\n"
        + "  importPath="
        + getImportPath()
        + "\n"
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GoIdeInfo goIdeInfo = (GoIdeInfo) o;
    return Objects.equals(sources, goIdeInfo.sources)
        && Objects.equals(importPath, goIdeInfo.importPath)
        && Objects.equals(libraryLabel, goIdeInfo.libraryLabel);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sources, importPath, libraryLabel);
  }

  /** Replaces import path from the aspect based on target label and kind. */
  public interface ImportPathReplacer {
    ExtensionPointName<ImportPathReplacer> EP_NAME =
        ExtensionPointName.create("com.google.idea.blaze.GoImportPathReplacer");

    boolean shouldReplace(@Nullable String existingImportPath);

    String getReplacement(Label label, Kind kind);
  }
}
