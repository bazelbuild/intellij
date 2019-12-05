package com.google.idea.sdkcompat.vcs;

import com.intellij.util.ui.VcsExecutablePathSelector;
import java.util.function.Consumer;

/** #api192: Constructor takes extra argument */
public class VcsExecutablePathSelectorAdapter extends VcsExecutablePathSelector {
  public VcsExecutablePathSelectorAdapter(String vcsName, Consumer<String> executableTester) {
    super(executableTester);
  }
}
