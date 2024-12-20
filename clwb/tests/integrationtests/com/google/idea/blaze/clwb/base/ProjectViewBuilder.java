package com.google.idea.blaze.clwb.base;

import java.util.ArrayList;
import java.util.List;

public class ProjectViewBuilder {
  private final List<String> directories = new ArrayList<>();
  private final List<String> syncFlags = new ArrayList<>();

  private boolean deriveTargetsFromDirectories = true;

  public void addDirectory(String directory) {
    directories.add(directory);
  }

  public void addSyncFlag(String flag) {
    syncFlags.add(flag);
  }

  public void setDeriveTargetsFromDirectories(boolean value) {
    deriveTargetsFromDirectories = value;
  }

  public String build() {
    final var builder = new StringBuilder();

    if (!directories.isEmpty()) {
      builder.append("directories:\n");
      directories.stream().map(ProjectViewBuilder::toListEntry).forEach(builder::append);
    }

    if (!syncFlags.isEmpty()) {
      builder.append("sync_flags:\n");
      syncFlags.stream().map(ProjectViewBuilder::toListEntry).forEach(builder::append);
    }

    builder.append(String.format("derive_targets_from_directories: %s%n", deriveTargetsFromDirectories));

    return builder.toString();
  }

  private static String toListEntry(String value) {
    return String.format("  %s%n", value);
  }
}
