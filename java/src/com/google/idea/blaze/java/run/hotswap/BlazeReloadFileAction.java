package com.google.idea.blaze.java.run.hotswap;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.build.BlazeBuildService;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

public class BlazeReloadFileAction extends AnAction {

    public static final String ACTION_ID = "Debugger.ReloadFile";

    private static final Logger LOGGER = Logger.getInstance(BlazeReloadFileAction.class);
    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile("^(?:\\$\\w+)*$");

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

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                BlazeBuildOutputs outputs = blazeBuildOutputsFuture.get();
                if (outputs.buildResult.status != BuildResult.Status.SUCCESS) {
                    LOGGER.debug("Build failed during hotswap action, hotswap will not be performed");
                    return;
                }

                ApplicationManager.getApplication().invokeLater(() -> {
                    HotSwapProgressImpl hotSwapProgress = new HotSwapProgressImpl(project);
                    DebuggerSession session = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
                    assert session != null;
                    hotSwapProgress.setDebuggerSession(session);

                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        findAndHotswapFile(project, vf, outputs, session, hotSwapProgress);
                    });
                });
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Exception occurred during building file to hotswap", e);
            }
        });
    }

    private void findAndHotswapFile(Project project, VirtualFile sourceFile, BlazeBuildOutputs outputs,
                                    DebuggerSession session, HotSwapProgressImpl progress) {
        ProgressManager.getInstance().runProcess(() -> {
            Path relative = ReadAction.compute(() ->
                    ProjectFileIndex.getInstance(project)
                            .getSourceRootForFile(sourceFile).toNioPath().
                            relativize(sourceFile.toNioPath()));
            File tempClassDir;
            try {
                tempClassDir = Files.createTempDirectory("IjBazelHotswap").toFile();
                tempClassDir.deleteOnExit();
            } catch (IOException e) {
                LOGGER.error("Failed creating temp directory for hotswap", e);
                progress.addMessage(session, MessageCategory.ERROR, "Failed setting environment up for hotswap");
                progress.finished();
                return;
            }

            try {
                String directory = relative.getParent().toString() + "/";
                String filenameWithoutExtension = sourceFile.getName().substring(0, sourceFile.getName().lastIndexOf('.'));

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
                                                    (entryFileNameWithoutExtention.length() == filenameWithoutExtension.length() ||
                                                        INNER_CLASS_PATTERN.matcher(entryFileNameWithoutExtention.substring(filenameWithoutExtension.length())).matches())) {
                                                File out = new File(tempClassDir, entryFileName);
                                                Files.copy(jis, out.toPath(), StandardCopyOption.REPLACE_EXISTING);

                                            }
                                        }
                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                LOGGER.error("Failed scanning and extracting matching files from jars to hotswap", e);
                                progress.addMessage(session, MessageCategory.ERROR, "Failed finding files to hotswap");
                            }
                        });

                Map<DebuggerSession, Map<String, HotSwapFile>> sessionMap = new HashMap<>();
                Map<String, HotSwapFile> hotSwapFileMap = new HashMap<>();
                sessionMap.put(session, hotSwapFileMap);
                for (File file : tempClassDir.listFiles()) {
                    String fileName = file.getName();
                    String name = directory.replace(Paths.DELIM, '.') +
                            (fileName.endsWith(".class") ?
                                    fileName.substring(0, fileName.indexOf(".class")) :
                                    fileName);
                    hotSwapFileMap.put(name, new HotSwapFile(file));
                }

                ProgressManager.checkCanceled();
                if (session.isRunning()) {
                    HotSwapManager.reloadModifiedClasses(sessionMap, progress);
                }
            } finally {
                tempClassDir.delete();
                progress.finished();

            }
        }, progress.getProgressIndicator());
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        DebuggerSession session =
                DebuggerManagerEx.getInstanceEx(e.getProject()).getContext().getDebuggerSession();
        e.getPresentation().setEnabled(session != null);
    }
}
