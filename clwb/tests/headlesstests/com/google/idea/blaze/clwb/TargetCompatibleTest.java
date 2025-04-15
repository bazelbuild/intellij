package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.run.producers.BlazeBuildFileRunConfigurationProducer;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.blaze.common.Label;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetCompatibleTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() throws Exception {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkTargets();
  }

  private void checkTargets() throws Exception {
    if (SystemInfo.isMac) {
      assertHasResolveConfiguration("main/linux.cc", false);
      assertHasResolveConfiguration("main/macos.cc", true);
      assertHasResolveConfiguration("main/windows.cc", false);
    } else if (SystemInfo.isLinux) {
      assertHasResolveConfiguration("main/linux.cc", true);
      assertHasResolveConfiguration("main/macos.cc", false);
      assertHasResolveConfiguration("main/windows.cc", false);
    } else if (SystemInfo.isWindows) {
      assertHasResolveConfiguration("main/linux.cc", false);
      assertHasResolveConfiguration("main/macos.cc", false);
      assertHasResolveConfiguration("main/windows.cc", true);
    }
  }

  private void assertHasResolveConfiguration(String relativePath, boolean present) {
    final var configurations = getWorkspace().getConfigurationsForFile(findProjectFile(relativePath));

    if (present) {
      assertThat(configurations).hasSize(1);
    } else {
      assertThat(configurations).isEmpty();
    }
  }
}
