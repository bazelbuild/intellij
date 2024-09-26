package com.google.idea.blaze.clwb.base;

import com.google.idea.blaze.base.sync.aspects.strategy.AspectRepositoryProvider;
import com.google.idea.testing.runfiles.Runfiles;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public class TestAspectRepositoryProvider implements AspectRepositoryProvider {

  @Override
  public Optional<File> aspectDirectory() {
    return Optional.of(Runfiles.runfilesPath("aspect")).map(Path::toFile);
  }
}
