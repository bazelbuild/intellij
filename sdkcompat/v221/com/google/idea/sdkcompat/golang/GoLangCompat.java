package com.google.idea.sdkcompat.golang;

import com.goide.execution.GoBuildingRunConfiguration.Kind;

/** Provides SDK compatibility shims for Go lang classes, available to IntelliJ UE. */
public class GoLangCompat {
  private GoLangCompat() {}

  /**
   * #api203: Kind enum moved from GoApplicationConfiguration to GoBuildingRunConfiguration in
   * 2021.1
   */
  public enum KindWrapper {
    DIRECTORY(Kind.DIRECTORY),
    PACKAGE(Kind.PACKAGE),
    FILE(Kind.FILE);

    private final Kind kind;

    private KindWrapper(Kind kind) {
      this.kind = kind;
    }

    public Kind getKind() {
      return this.kind;
    }
  }
}
