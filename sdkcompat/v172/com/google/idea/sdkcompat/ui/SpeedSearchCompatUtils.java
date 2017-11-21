package com.google.idea.sdkcompat.ui;

import com.intellij.ui.speedSearch.SpeedSearch;
import java.awt.event.KeyEvent;

/** SDK compat utils for {@link SpeedSearch}, API last modified in 173. */
public class SpeedSearchCompatUtils {

  public static void processKeyEvent(SpeedSearch search, KeyEvent e) {
    search.process(e);
  }
}
