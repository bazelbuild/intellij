package com.google.idea.blaze.java.run.hotswap;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.build.BlazeBuildService;
import com.google.idea.blaze.base.command.buildresult.BepArtifactData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.compiler.actions.CompileAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class BlazeReloadFileAction extends AnAction {

    public BlazeReloadFileAction(AnAction delegate) {
        super(
                delegate.getTemplatePresentation().getTextWithMnemonic(),
                delegate.getTemplatePresentation().getDescription(),
                delegate.getTemplatePresentation().getIcon());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        VirtualFile vf = anActionEvent.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
        ImmutableList<Label> targets = SourceToTargetMap.getInstance(anActionEvent.getProject())
                .getTargetsToBuildForSourceFile(new File(vf.getPath()));
        Future<BlazeBuildOutputs> blazeBuildOutputsFuture =
                BlazeBuildService.getInstance(anActionEvent.getProject()).buildFile(vf.getName(), targets);


        try {
            BlazeBuildOutputs outputs = blazeBuildOutputsFuture.get();
            for (BepArtifactData value : outputs.artifacts.values()) {
                value.artifact.getRelativePath();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
