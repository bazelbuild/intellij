package com.google.idea.blaze.ijwb.deprecation;

import com.intellij.ide.plugins.PluginReplacement;

class BazelPluginReplacement extends PluginReplacement {

  BazelPluginReplacement() {
    super("org.jetbrains.bazel");
  }
}