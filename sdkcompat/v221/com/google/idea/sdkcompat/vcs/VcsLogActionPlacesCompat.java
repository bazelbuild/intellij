package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.actionSystem.ActionPlaces;

/** #api212. Inline values into PiperChangeListProvider */
public final class VcsLogActionPlacesCompat {

  private VcsLogActionPlacesCompat() {}

  public static final String VCS_LOG_TOOLBAR_PLACE = ActionPlaces.VCS_LOG_TOOLBAR_PLACE;
  public static final String VCS_LOG_TOOLBAR_POPUP_PLACE = ActionPlaces.VCS_LOG_TOOLBAR_POPUP_PLACE;
  public static final String VCS_LOG_TABLE_PLACE = ActionPlaces.VCS_LOG_TABLE_PLACE;
  public static final String VCS_HISTORY_TOOLBAR_PLACE = ActionPlaces.VCS_HISTORY_TOOLBAR_PLACE;
  public static final String VCS_HISTORY_PLACE = ActionPlaces.VCS_HISTORY_PLACE;
}
