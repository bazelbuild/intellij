package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.java.run.RunUtil;
import com.google.idea.blaze.java.run.producers.TestSizeAnnotationMap;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil;
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2ConfigurationProducer;
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2RunConfiguration;
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider;
import scala.Option;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind.TEST_NAME;

public class BlazeSpecs2ConfigurationProducer
        extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

    private final Specs2ConfigurationProducer producer = TestConfigurationUtil.specs2ConfigurationProducer();

    public BlazeSpecs2ConfigurationProducer() {
        super(BlazeCommandRunConfigurationType.getInstance());
    }

    @Override
    protected boolean doSetupConfigFromContext(BlazeCommandRunConfiguration configuration,
                                               ConfigurationContext context,
                                               Ref<PsiElement> sourceElement) {
        Pair<PsiClass, Specs2RunConfiguration> pair = getSpecs2Configuration(context);
        if (pair == null)
            return false;

        PsiClass testClass = pair.first;
        Specs2RunConfiguration specs2Config = pair.second;

        BlazeCommandRunConfigurationCommonState handlerState =
                configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null) {
            return false;
        }

        handlerState.getCommandState().setCommand(BlazeCommandName.TEST);

        // remove old test filter flag if present
        List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
        flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
        flags.add(getTestFilterFlag(specs2Config, context));
        handlerState.getBlazeFlagsState().setRawFlags(flags);

        configuration.setName(getTestDisplayName(specs2Config));
        configuration.setNameChangedByUser(true); // don't revert to generated name

        TargetIdeInfo target = getTargetIdeInfo(testClass);
        configuration.setTarget(target.key.label);

        return true;
    }

    private String getTestDisplayName(Specs2RunConfiguration config) {
        if (config.getTestKind() == TEST_NAME)
            return config.testName();

        return config.getName();
    }

    private TargetIdeInfo getTargetIdeInfo(PsiClass testClass) {
        TestIdeInfo.TestSize testSize = TestSizeAnnotationMap.getTestSize(testClass);
        return RunUtil.targetForTestClass(testClass, testSize);
    }

    @Override
    protected boolean doIsConfigFromContext(BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
        Pair<PsiClass, Specs2RunConfiguration> pair = getSpecs2Configuration(context);
        if (pair == null)
            return false;

        BlazeCommandRunConfigurationCommonState handlerState =
                configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null
                || !Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.TEST)
                || !Objects.equals(handlerState.getTestFilterFlag(), getTestFilterFlag(pair.second, context))) {
            return false;
        }
        TargetIdeInfo target = getTargetIdeInfo(pair.first);
        return target != null && Objects.equals(configuration.getTarget(), target.key.label);
    }

    @Override
    public boolean shouldReplace(ConfigurationFromContext self, ConfigurationFromContext other) {
        return true;
    }

    private static String getTestFilterFlag(Specs2RunConfiguration specs2Config, ConfigurationContext context) {
        String scope = Specs2TestConfigurationUtil.getParentScope(context.getLocation().getPsiElement());

        String filter = specs2Config.getTestClassPath() + "#" + scope + specs2Config.getTestName();

        // todo escape fragment if needed (e.g. contains + or other regex-specific characters)
        return BlazeFlags.TEST_FILTER + "=" + filter;
    }



    @Nullable
    private Pair<PsiClass, Specs2RunConfiguration> getSpecs2Configuration(ConfigurationContext context) {
        Option<Tuple2<PsiElement, RunnerAndConfigurationSettings>> c = producer.createConfigurationByLocation(context.getLocation());
        if (c.isEmpty())
            return null;
        PsiElement element = c.get()._1();
        RunnerAndConfigurationSettings settings = c.get()._2();

        return Pair.create((PsiClass)element, (Specs2RunConfiguration)settings.getConfiguration());
    }
}
