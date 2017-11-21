package com.google.idea.blaze.scala.run;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.util.List;

public class BlazeScalaTestEventHandler implements BlazeTestEventsHandler {
    @Override
    public boolean handlesKind(@Nullable Kind kind) {
        return kind == Kind.SCALA_JUNIT_TEST;
    }

    @Nullable
    @Override
    public SMTestLocator getTestLocator() {
        return JavaTestLocator.INSTANCE;
    }

    @Nullable
    @Override
    public String getTestFilter(Project project, List<Location<?>> testLocations) {
        return null;
    }

    @Override
    public String testDisplayName(@Nullable Kind kind, String rawName) {
        int index = rawName.lastIndexOf(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER);
        return index != -1 ? rawName.substring(index + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER.length()) : rawName;
    }
}
