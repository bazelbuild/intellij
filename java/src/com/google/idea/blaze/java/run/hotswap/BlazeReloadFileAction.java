package com.google.idea.blaze.java.run.hotswap;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.build.BlazeBuildService;
import com.google.idea.blaze.base.command.buildresult.BepArtifactData;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.ui.HotSwapProgressImpl;
import com.intellij.history.core.Paths;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

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


        HotSwapProgressImpl hotSwapProgress = new HotSwapProgressImpl(anActionEvent.getProject());
        DebuggerSession session = DebuggerManagerEx.getInstanceEx(anActionEvent.getProject()).getContext().getDebuggerSession();
        hotSwapProgress.setDebuggerSession(session);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                BlazeBuildOutputs outputs = blazeBuildOutputsFuture.get();
                File temp = Files.createTempDirectory("ij").toFile();
                temp.deleteOnExit();

                Path relative = ReadAction.compute(() -> {
                    return ProjectFileIndex.getInstance(anActionEvent.getProject()).getSourceRootForFile(vf).toNioPath().relativize(vf.toNioPath());
                });
                String p = relative.getParent().toString() + "/";
                for (BepArtifactData value : outputs.artifacts.values()) {
                    if (value.artifact instanceof LocalFileOutputArtifact && value.artifact.getRelativePath().endsWith(".jar")) {
                        LocalFileOutputArtifact local = (LocalFileOutputArtifact) value.artifact;
                        try (JarInputStream jis = new JarInputStream(new FileInputStream(local.getFile()))) {
                            ZipEntry entry;
                            while ((entry = jis.getNextEntry()) != null) {
                                if (entry.isDirectory() && entry.getName().equals(p)) {
                                    while ((entry = jis.getNextEntry()) != null && !entry.isDirectory()) {
                                        String fileName = entry.getName().substring(entry.getName().lastIndexOf(Paths.DELIM) + 1);
                                        if (fileName.startsWith(vf.getName().substring(0, vf.getName().indexOf(".java")))) {
                                            File out = new File(temp, fileName);
                                            Files.copy(jis, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }

                Map<DebuggerSession, Map<String, HotSwapFile>> sessionMap = new HashMap<>();
                Map<String, HotSwapFile> hotSwapFileMap = new HashMap<>();
                sessionMap.put(session, hotSwapFileMap);
                for (File file : temp.listFiles()) {
                    hotSwapFileMap.put(p.replace(Paths.DELIM, '.') + file.getName().substring(0, file.getName().indexOf(".class")), new HotSwapFile(file));
                }

                ProgressManager.getInstance().runProcess(() -> {
                    HotSwapManager.reloadModifiedClasses(sessionMap, hotSwapProgress);
                    hotSwapProgress.finished();
                }, hotSwapProgress.getProgressIndicator());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException | IOException e) {
                throw new RuntimeException(e);
            }
        });

    }
}
