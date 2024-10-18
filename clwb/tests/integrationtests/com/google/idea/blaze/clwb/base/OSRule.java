package com.google.idea.blaze.clwb.base;

import java.util.Arrays;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import com.intellij.util.system.OS;

public class OSRule implements TestRule {

  private final OS[] supportedOS;

  public OSRule(OS... supportedOS) {
    this.supportedOS = supportedOS;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    if (Arrays.stream(supportedOS).anyMatch(it -> it == OS.CURRENT)) {
      return base;
    }

    return Statements.message(
        "Test '%s' does not run on %s",
        description.getDisplayName(),
        OS.CURRENT.name()
    );
  }
}
