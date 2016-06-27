/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.source;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;

import javax.annotation.Nullable;
import java.util.Map;

public class ManifestFilePackageReader extends JavaPackageReader {

  private final Map<Label, Map<String, String>> manifestMap;

  public ManifestFilePackageReader(Map<Label, Map<String, String>> manifestMap) {
    this.manifestMap = manifestMap;
  }

  @Nullable
  @Override
  String getDeclaredPackageOfJavaFile(BlazeContext context, SourceArtifact sourceArtifact) {
    Map<String, String> manifestMapForRule = manifestMap.get(sourceArtifact.originatingRule);
    if (manifestMapForRule != null) {
      return manifestMapForRule.get(sourceArtifact.artifactLocation.getFile().getPath());
    }
    return null;
  }
}
