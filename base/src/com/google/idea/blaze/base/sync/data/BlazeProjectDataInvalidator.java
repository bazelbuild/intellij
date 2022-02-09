package com.google.idea.blaze.base.sync.data;

import com.google.idea.blaze.base.io.FileOperationProvider;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class BlazeProjectDataInvalidator extends CachesInvalidator {
  private static final Logger logger =
      Logger.getInstance(BlazeProjectDataInvalidator.class.getName());

  @Override
  public @Nullable @NlsContexts.Checkbox String getDescription() {
    return "Clear all Bazel caches";
  }

  @Override
  public @Nullable @NlsContexts.DetailedDescription String getComment() {
    return "Invalidates all IDE related data for all Bazel projects";
  }

  @Override
  public @Nullable Boolean optionalCheckboxDefaultValue() {
    return false;
  }

  @Override
  public void invalidateCaches() {
    try {
      File configurationDir = BlazeDataStorage.getProjectConfigurationDir();
      if (configurationDir.exists()) {
        FileOperationProvider.getInstance().deleteDirectoryContents(configurationDir, true);
      }
    } catch (IOException e) {
      logger.warn(e);
    }
  }
}
