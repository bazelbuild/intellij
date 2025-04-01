package com.google.idea.blaze.skylark.debugger.run;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.dashboard.actions.ExecutorAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class SkylarkDebugAction extends ExecutorAction {
    @Override
    protected Executor getExecutor() {
        return ExecutorRegistry.getInstance().getExecutorById(SkylarkDebugExecutor.ID);
    }

    @Override
    protected void update(@NotNull AnActionEvent e, boolean running) {

    }
}
