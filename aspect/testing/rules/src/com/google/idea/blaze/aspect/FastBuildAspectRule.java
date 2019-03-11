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
package com.google.idea.blaze.aspect;


import com.google.common.base.Splitter;
import com.google.devtools.intellij.aspect.FastBuildAspectTestFixtureOuterClass.FastBuildAspectTestFixture;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** A rule for interacting with the fast build aspect text fixture. */
public final class FastBuildAspectRule implements TestRule {

  private final Path base;
  private Path packagePath;

  public FastBuildAspectRule(String base) {
    this.base = Paths.get(base);
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        List<String> pathElements =
            Splitter.on('.').splitToList(description.getTestClass().getPackage().getName());
        packagePath = pathElements.stream().map(Paths::get).reduce(base, Path::resolve);

        try {
          statement.evaluate();
        } finally {
          packagePath = null;
        }
      }
    };
  }

  public FastBuildAspectTestFixture loadTestFixture(String testRelativeLabel) throws IOException {
    String label = testRelative(testRelativeLabel);
    String relativePath = label.substring(2).replace(':', '/') + ".fast-build-aspect-test-fixture";
    try (InputStream inputStream = new FileInputStream(relativePath)) {
      return FastBuildAspectTestFixture.parseFrom(inputStream);
    }
  }

  public String testRelative(String path) {
    String relativePath =
        path.startsWith(":")
            ? packagePath + path
            : Paths.get(packagePath.toString(), path).toString();
    return path.contains(":") ? "//" + relativePath : relativePath;
  }
}
