/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.io.FileAttributeScanner;
import java.io.IOException;
import java.io.InputStream;

/** An output artifact from a blaze build. */
public interface OutputArtifact {

  TimestampReader TIMESTAMP_READER = new TimestampReader();

  /** An {@link AttributeReader} for OutputArtifacts returning last modified time. */
  final class TimestampReader
      implements FileAttributeScanner.AttributeReader<OutputArtifact, Long> {
    private TimestampReader() {}

    @Override
    public Long getAttribute(OutputArtifact artifact) {
      return artifact.getLastModifiedTime();
    }

    @Override
    public boolean isValid(Long attribute) {
      return attribute != 0;
    }
  }

  /**
   * Returns the last modified time of this artifact, in milliseconds from the epoch, or 0 if this
   * can't be determined.
   */
  long getLastModifiedTime();

  /** Returns the length of the underlying file in bytes, or 0 if this can't be determined. */
  long getLength();

  /** The path component related to the build configuration. */
  String getBlazeConfigurationString(BlazeConfigurationHandler handler);

  /** An input stream providing the contents of this artifact. */
  InputStream getInputStream() throws IOException;

  /**
   * A unique key used for serialization, derived from the artifact's file path. This is a 1-way
   * mapping; it's not intended to be mappable back to an OutputArtifact.
   */
  String getKey();
}
