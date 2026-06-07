/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Extension point for transforming {@link ArtifactLocation} objects during sync.
 *
 * <p>This allows customizing how artifact locations are processed when converting from proto
 * to the IDE model. Common use cases include:
 * <ul>
 *   <li>Mapping generated artifacts to their source equivalents
 *   <li>Rewriting paths for specific file patterns
 *   <li>Changing artifact properties (is_source, is_external)
 * </ul>
 *
 * <p>Transformers can filter by file extension or pattern to apply language-specific logic.
 */
public interface ArtifactLocationTransformer {
  ExtensionPointName<ArtifactLocationTransformer> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.ArtifactLocationTransformer");

  /**
   * Transform an artifact location.
   *
   * @param artifact the original artifact location
   * @return the transformed artifact location, or the original if no transformation is needed
   */
  ArtifactLocation transform(ArtifactLocation artifact);
}
