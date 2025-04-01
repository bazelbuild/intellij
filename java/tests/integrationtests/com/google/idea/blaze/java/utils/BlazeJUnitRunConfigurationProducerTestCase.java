package com.google.idea.blaze.java.utils;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags.JUnitVersion;
import com.intellij.psi.PsiFile;
import org.junit.Before;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Base class that parameterizes test to test both JUnit 4 and JUnit 5.
 */
public abstract class BlazeJUnitRunConfigurationProducerTestCase  extends
    BlazeRunConfigurationProducerTestCase {

  @Parameter
  public JUnitVersion jUnitVersionUnderTest;

  @Parameters(name = "{0}")
  public static JUnitVersion[] params() {
    return JUnitTestUtils.JUNIT_VERSIONS_UNDER_TEST;
  }

  @Before
  public final void setup() {
    JUnitTestUtils.setupForJUnitTests(workspace, fileSystem);
  }

  protected void setUpRepositoryAndTarget() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass")
                    .addSource(sourceRoot("java/com/google/test/TestClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
  }

  protected PsiFile createAndIndexGenericJUnitTestFile() throws Throwable {
    return createAndIndexGenericJUnitTestFile("TestClass");
  }

  protected PsiFile createAndIndexGenericJUnitTestFile(String testClass) throws Throwable {
    PsiFile javaFile = JUnitTestUtils.createGenericJUnitFile(workspace, jUnitVersionUnderTest, testClass);
    editorTest.openFileInEditor(javaFile);
    return javaFile;
  }

}
