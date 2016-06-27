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
package com.google.idea.blaze.base.scope.output;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Output;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * An issue in a blaze operation.
 */
public class IssueOutput implements Output {

  public static final int NO_LINE = -1;
  public static final int NO_COLUMN = -1;

  @Nullable private final File file;
  private final int line;
  private final int column;
  @NotNull private final Category category;
  @NotNull private final String message;
  @Nullable Navigatable navigatable;
  @Nullable IssueData issueData;

  public static class IssueData {}

  public enum Category {
    ERROR,
    WARNING,
    STATISTICS,
    INFORMATION
  }

  @NotNull
  public static Builder issue(@NotNull Category category, @NotNull String message) {
    return new Builder(category, message);
  }

  @NotNull
  public static Builder error(@NotNull String message) {
    return new Builder(Category.ERROR, message);
  }

  @NotNull
  public static Builder warn(@NotNull String message) {
    return new Builder(Category.WARNING, message);
  }

  public static class Builder {
    @NotNull private final Category category;
    @NotNull private final String message;
    @Nullable private File file;
    private int line = NO_LINE;
    private int column = NO_COLUMN;
    @Nullable Navigatable navigatable;
    @Nullable IssueData issueData;

    public Builder(@NotNull Category category, @NotNull String message) {
      this.category = category;
      this.message = message;
    }

    @NotNull
    public Builder inFile(@Nullable File file) {
      this.file = file;
      return this;
    }

    @NotNull
    public Builder onLine(int line) {
      this.line = line;
      return this;
    }

    @NotNull
    public Builder inColumn(int column) {
      this.column = column;
      return this;
    }

    @NotNull
    public Builder withData(@Nullable IssueData issueData) {
      this.issueData = issueData;
      return this;
    }

    @NotNull
    public Builder navigatable(@Nullable Navigatable navigatable) {
      this.navigatable = navigatable;
      return this;
    }

    public IssueOutput build() {
      return new IssueOutput(file, line, column, navigatable, category, message, issueData);
    }

    public void submit(@NotNull BlazeContext context) {
      context.output(build());
      if (category == Category.ERROR) {
        context.setHasError();
      }
    }
  }

  private IssueOutput(
    @Nullable File file,
    int line,
    int column,
    @Nullable Navigatable navigatable,
    @NotNull Category category,
    @NotNull String message,
    @Nullable IssueData issueData) {
    this.file = file;
    this.line = line;
    this.column = column;
    this.navigatable = navigatable;
    this.category = category;
    this.message = message;
    this.issueData = issueData;
  }

  @Nullable
  public File getFile() {
    return file;
  }

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }

  @Nullable
  public Navigatable getNavigatable() {
    return navigatable;
  }

  @NotNull
  public Category getCategory() {
    return category;
  }

  @NotNull
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return message;
  }

  @Nullable
  public IssueData getIssueData() {
    return issueData;
  }
}
