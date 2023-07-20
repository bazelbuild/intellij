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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
        Project project = anActionEvent.getProject();
        VirtualFile vf = anActionEvent.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
        ImmutableList<Label> targets = SourceToTargetMap.getInstance(project)
                .getTargetsToBuildForSourceFile(new File(vf.getPath()));
        Future<BlazeBuildOutputs> blazeBuildOutputsFuture =
                BlazeBuildService.getInstance(project).buildFile(vf.getName(), targets);


        Path relative = ReadAction.compute(() ->
                ProjectFileIndex.getInstance(project).getSourceRootForFile(vf).toNioPath().relativize(vf.toNioPath()));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                BlazeBuildOutputs outputs = blazeBuildOutputsFuture.get();
                File temp = Files.createTempDirectory("IjBazelHotswap").toFile();
                temp.deleteOnExit();

                ApplicationManager.getApplication().invokeLater(() -> {
                    HotSwapProgressImpl hotSwapProgress = new HotSwapProgressImpl(project);
                    DebuggerSession session = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
                    assert session != null;
                    hotSwapProgress.setDebuggerSession(session);

                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        ProgressManager.getInstance().runProcess(() -> {
                            try {
                                String directory = relative.getParent().toString() + "/";
                                String filenameWithoutExtension = vf.getName().substring(0, vf.getName().lastIndexOf('.'));

                                outputs.artifacts.values().parallelStream()
                                        .filter(value -> value.artifact instanceof LocalFileOutputArtifact && value.artifact.getRelativePath().endsWith(".jar"))
                                        .map(value -> (LocalFileOutputArtifact) value.artifact)
                                        .forEach(artifact -> {
                                            ProgressManager.checkCanceled();
                                            try (JarInputStream jis = new JarInputStream(new FileInputStream(artifact.getFile()))) {
                                                ZipEntry entry;
                                                while ((entry = jis.getNextEntry()) != null) {
                                                    if (entry.isDirectory() && entry.getName().equals(directory)) {
                                                        while ((entry = jis.getNextEntry()) != null && !entry.isDirectory()) {
                                                            String entryFileName = entry.getName().substring(entry.getName().lastIndexOf(Paths.DELIM) + 1);
                                                            String entryFileNameWithoutExtention = entryFileName.contains(".") ? entryFileName.substring(0, entryFileName.lastIndexOf('.')) : entryFileName;
                                                            if (entryFileNameWithoutExtention.startsWith(filenameWithoutExtension) &&
                                                                    (entryFileNameWithoutExtention.length() == filenameWithoutExtension.length() || entryFileNameWithoutExtention.charAt(filenameWithoutExtension.length()) == '$')) {
                                                                File out = new File(temp, entryFileName);
                                                                Files.copy(jis, out.toPath(), StandardCopyOption.REPLACE_EXISTING);

                                                            }
                                                        }
                                                        break;
                                                    }
                                                }
                                            } catch (IOException e) {
                                                throw new RuntimeException(e);
                                            }
                                        });


                                Map<DebuggerSession, Map<String, HotSwapFile>> sessionMap = new HashMap<>();
                                Map<String, HotSwapFile> hotSwapFileMap = new HashMap<>();
                                sessionMap.put(session, hotSwapFileMap);
                                for (File file : temp.listFiles()) {
                                    hotSwapFileMap.put(directory.replace(Paths.DELIM, '.') + file.getName().substring(0, file.getName().indexOf(".class")), new HotSwapFile(file));
                                }

                                ProgressManager.checkCanceled();
                                if (session.isRunning()) {
                                    HotSwapManager.reloadModifiedClasses(sessionMap, hotSwapProgress);
                                }
                            } finally {
                                hotSwapProgress.finished();

                            }
                        }, hotSwapProgress.getProgressIndicator());
                    });
                });
            } catch (InterruptedException | ExecutionException | IOException e) {
                throw new RuntimeException(e);
            }
        });

    }
}
