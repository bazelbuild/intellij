package com.google.idea.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.rules.ExternalResource;

/**
 * Test rule to disable a specified list of plugins during a test class.
 *
 * <p>Disabled plugins only take effect when initializing the {@link Application}, which can only
 * happen once per integration test target.
 *
 * <p>As such, all users of this test rule must be in their own `java_test` target.
 */
public class DisablePluginsTestRule extends ExternalResource {

  private final ImmutableList<String> disabledPluginIds;

  public DisablePluginsTestRule(ImmutableList<String> disabledPluginIds) {
    this.disabledPluginIds = Preconditions.checkNotNull(disabledPluginIds);
  }

  @Override
  protected void before() throws Throwable {
    if (ApplicationManager.getApplication() != null) {
      // We may be able to relax this constraint if we check that the desired
      // disabledPluginIds matches the existing value of ourDisabledPlugins.
      throw new RuntimeException("Cannot disable plugins; they've already been loaded.");
    }
    forceSetDisabledPluginsField(disabledPluginIds);
  }

  @Override
  protected void after() {
    // no point resetting the list of disabled plugins to its prior value -- subsequent tests can't
    // reinitialize the {@link Application} anyway.
  }

  /**
   * Access the 'ourDisabledPlugins' field in {@link PluginManagerCore} via reflection, and set it.
   * We can't simply populate a 'disabled_plugins.txt' file (the normal mechanism for disabling
   * plugins), because that is ignored during tests.
   */
  private static void forceSetDisabledPluginsField(List<String> disabledPlugins)
      throws NoSuchFieldException, IllegalAccessException {
    Field ourDisabledPlugins = PluginManagerCore.class.getDeclaredField("ourDisabledPlugins");
    ourDisabledPlugins.setAccessible(true);
    ourDisabledPlugins.set(null, disabledPlugins);
    ourDisabledPlugins.setAccessible(false);
  }
}
