/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.producer;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.EditorTestHelper;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.DataManager;
import com.intellij.idea.CommandLineApplication.MyDataManagerImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.MapDataContext;
import java.util.Arrays;
import javax.annotation.Nullable;
import org.junit.Before;

/** Run configuration producer integration test base */
public class BlazeRunConfigurationProducerTestCase extends BlazeIntegrationTestCase {

  protected EditorTestHelper editorTest;

  @Before
  public final void doSetup() {
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    editorTest = new EditorTestHelper(getProject(), testFixture);

    // IntelliJ replaces the normal DataManager with a mock version in headless environments.
    // We rely on a functional DataManager in run configuration tests to recognize when multiple
    // psi elements are selected.
    DataManager dataManager =
        new MyDataManagerImpl() {
          DataContext dataContext;

          @Override
          public <T> void saveInDataContext(
              DataContext dataContext, Key<T> dataKey, @Nullable T data) {
            this.dataContext = dataContext;
            super.saveInDataContext(dataContext, dataKey, data);
          }

          @Override
          public DataContext getDataContext() {
            return dataContext != null ? dataContext : super.getDataContext();
          }
        };
    registerApplicationComponent(DataManager.class, dataManager);
  }

  protected PsiFile createAndIndexFile(WorkspacePath path, String... contents) {
    PsiFile file = workspace.createPsiFile(path, contents);
    editorTest.openFileInEditor(file); // open file to trigger update of indices
    return file;
  }

  @Nullable
  protected static String getTestFilterContents(BlazeCommandRunConfiguration config) {
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return handlerState != null ? handlerState.getTestFilterFlag() : null;
  }

  @Nullable
  protected static BlazeCommandName getCommandType(BlazeCommandRunConfiguration config) {
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return handlerState != null ? handlerState.getCommandState().getCommand() : null;
  }

  protected ConfigurationContext createContextFromPsi(PsiElement element) {
    final MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, getProject());
    dataContext.put(LangDataKeys.MODULE, ModuleUtil.findModuleForPsiElement(element));
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(element));
    return ConfigurationContext.getFromContext(dataContext);
  }

  protected ConfigurationContext createContextFromMultipleElements(PsiElement[] elements) {
    final MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, getProject());
    dataContext.put(LangDataKeys.MODULE, ModuleUtil.findModuleForPsiElement(elements[0]));
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(elements[0]));
    dataContext.put(
        Location.DATA_KEYS,
        Arrays.stream(elements).map(PsiLocation::fromPsiElement).toArray(Location[]::new));
    dataContext.put(LangDataKeys.PSI_ELEMENT_ARRAY, elements);
    return ConfigurationContext.getFromContext(dataContext);
  }

  @Nullable
  protected RunConfiguration createConfigurationFromLocation(PsiFile psiFile) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, getProject());
    dataContext.put(LangDataKeys.MODULE, ModuleUtil.findModuleForPsiElement(psiFile));
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiFile));
    RunnerAndConfigurationSettings settings =
        ConfigurationContext.getFromContext(dataContext).getConfiguration();
    return settings != null ? settings.getConfiguration() : null;
  }

  protected static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
