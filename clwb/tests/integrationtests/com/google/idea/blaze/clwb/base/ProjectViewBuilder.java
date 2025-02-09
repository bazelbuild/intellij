package com.google.idea.blaze.clwb.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProjectViewBuilder {

  private final List<String> directories = new ArrayList<>();
  private final List<String> build_flags = new ArrayList<>();
  private final List<String> sync_flags = new ArrayList<>();

  private boolean derive_targets_from_directories = true;
  private boolean use_query_sync = false;

  public ProjectViewBuilder addDirectories(String... values) {
    directories.addAll(Arrays.asList(values));
    return this;
  }

  public ProjectViewBuilder addRootDirectory() {
    directories.add(".");
    return this;
  }

  public ProjectViewBuilder addBuildFlag(String... values) {
    build_flags.addAll(Arrays.asList(values));
    return this;
  }

  public ProjectViewBuilder addSyncFlag(String... values) {
    sync_flags.addAll(Arrays.asList(values));
    return this;
  }

  public ProjectViewBuilder setDeriveTargetsFromDirectories(boolean value) {
    derive_targets_from_directories = value;
    return this;
  }

  public ProjectViewBuilder useQuerySync(boolean value) {
    use_query_sync = value;
    return this;
  }

  @Override
  public String toString() {
    final var builder = new StringBuilder();

    if (!directories.isEmpty()) {
      builder.append("directories:\n");
      directories.stream().map(ProjectViewBuilder::toElement).forEach(builder::append);
    }

    if (!build_flags.isEmpty()) {
      builder.append("build_flags:\n");
      build_flags.stream().map(ProjectViewBuilder::toElement).forEach(builder::append);
    }

    if (!sync_flags.isEmpty()) {
      builder.append("sync_flags:\n");
      sync_flags.stream().map(ProjectViewBuilder::toElement).forEach(builder::append);
    }

    builder.append(String.format("derive_targets_from_directories: %b%n", derive_targets_from_directories));
    builder.append(String.format("use_query_sync: %b%n", use_query_sync));

    return builder.toString();
  }

  private static String toElement(String value) {
    return String.format("  %s%n", value);
  }
}
