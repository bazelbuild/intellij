package com.google.idea.sdkcompat.ui;

import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import java.awt.Color;
import javax.annotation.Nullable;

/** SDK compat utils for {@link ComponentPopupBuilder}; API last modified in 173. */
public final class ComponentPopupBuilderCompatUtils {

  /** Set the color of the popup's border. */
  public static void setBorderColor(ComponentPopupBuilder popupBuilder, @Nullable Color color) {
    // Not supported until 173.
  }
}
