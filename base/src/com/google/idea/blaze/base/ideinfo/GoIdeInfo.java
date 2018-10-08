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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.io.Serializable;
import javax.annotation.Nullable;

/** Ide info specific to go rules. */
public class GoIdeInfo implements Serializable {
  private static final long serialVersionUID = 2L;

  private final ImmutableList<ArtifactLocation> sources;
  private final String importPath;

  private GoIdeInfo(
      Label label,
      Kind kind,
      ImmutableList<ArtifactLocation> sources,
      @Nullable String importPath) {
    for (ImportPathReplacer fixer : ImportPathReplacer.EP_NAME.getExtensions()) {
      if (fixer.shouldReplace(importPath)) {
        importPath = fixer.getReplacement(label, kind);
        break;
      }
    }
    this.sources = sources;
    this.importPath = importPath;
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  @Nullable
  public String getImportPath() {
    return importPath;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for go rule info */
  public static class Builder {
    private Label label = null;
    private Kind kind = null;
    private final ImmutableList.Builder<ArtifactLocation> sources = ImmutableList.builder();
    @Nullable private String importPath = null;

    public Builder setLabel(Label label) {
      this.label = label;
      return this;
    }

    public Builder setKind(Kind kind) {
      this.kind = kind;
      return this;
    }

    public Builder addSources(Iterable<ArtifactLocation> sources) {
      this.sources.addAll(sources);
      return this;
    }

    public Builder setImportPath(@Nullable String importPath) {
      this.importPath = importPath;
      return this;
    }

    public GoIdeInfo build() {
      return new GoIdeInfo(label, kind, sources.build(), importPath);
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

  /** Replaces import path from the aspect based on target label and kind. */
  public interface ImportPathReplacer {
    ExtensionPointName<ImportPathReplacer> EP_NAME =
        ExtensionPointName.create("com.google.idea.blaze.GoImportPathReplacer");

    boolean shouldReplace(@Nullable String existingImportPath);

    String getReplacement(Label label, Kind kind);
  }
}
