package com.google.idea.blaze.base.sync.data;

import com.google.idea.blaze.base.io.FileOperationProvider;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;

public class BlazeProjectDataInvalidator extends CachesInvalidator {
  private static final Logger logger =
      Logger.getInstance(BlazeProjectDataInvalidator.class.getName());

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
