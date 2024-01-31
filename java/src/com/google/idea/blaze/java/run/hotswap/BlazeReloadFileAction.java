package com.google.idea.blaze.java.run.hotswap;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.build.BlazeBuildService;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.HotSwapProgressImpl;
import com.intellij.debugger.ui.HotSwapUIImpl;
import com.intellij.debugger.ui.RunHotswapDialog;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.history.core.Paths;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class BlazeReloadFileAction extends AnAction {

    public static final String ACTION_ID = "Debugger.ReloadFile";

    private static final Logger LOGGER = Logger.getInstance(BlazeReloadFileAction.class);
    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile("^(?:\\$\\w+)*$");

    private final AnAction delegate;

    public BlazeReloadFileAction(AnAction delegate) {
        super(
                delegate.getTemplatePresentation().getTextWithMnemonic(),
                delegate.getTemplatePresentation().getDescription(),
                delegate.getTemplatePresentation().getIcon());
        this.delegate = delegate;
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

                if (!DebuggerSettings.RUN_HOTSWAP_NEVER.equals(DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE)) {
                    Path relative = ReadAction.compute(() ->
                            ProjectFileIndex.getInstance(project)
                                    .getSourceRootForFile(vf).toNioPath().
                                    relativize(vf.toNioPath()));
                    String jarDirectory = relative.getParent().toString() + Paths.DELIM;
                    File tempOutputDir;
                    try {
                        tempOutputDir = Files.createTempDirectory("IjBazelHotswap").toFile();
                        LOGGER.debug("Temp dir for HotSwap artifacts is " + tempOutputDir.getPath());
                        tempOutputDir.deleteOnExit();
                    } catch (IOException e) {
                        LOGGER.error("Failed creating temp directory for hotswap", e);
                        return;
                    }

                    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Preparing to hotswap", true) {
                        @Override
                        public void run(@NotNull ProgressIndicator progressIndicator) {
                            // We want to be aware of entries like darwin_arm64-fastbuild/bin/some-api/lib-api.jar and not interested in darwin_arm64-fastbuild/external-api-0.0.654.jar
                            // TODO: It is a good idea to skip non-output artifacts.
                            Supplier<List<String>> artifactsForLogging = Suppliers.memoize(() -> outputs.artifacts.keySet().stream()
                                    .filter(artifact -> artifact.indexOf("/") != artifact.lastIndexOf("/"))
                                    .collect(Collectors.toList()));
                            // With Gateway in some cases actions got lost, so we want to log this action for every invocation
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug(String.format("Searching for classes to HotSwap for class %s with package %s among outputs %s", vf.getPath(), jarDirectory, artifactsForLogging.get()));
                            } else {
                                LOGGER.info(String.format("Searching for classes to HotSwap for class %s with package %s", vf.getPath(), jarDirectory));
                            }

                            findAndCopyOutputFile(vf, tempOutputDir, jarDirectory, outputs);
                            if (tempOutputDir.listFiles().length > 0) {
                                LOGGER.info(String.format("Invoking HotSwap for entries %s with package %s", Arrays.toString(tempOutputDir.listFiles()), jarDirectory));
                                hotswapFile(project, jarDirectory, tempOutputDir);
                            } else {
                                LOGGER.warn(String.format("There was nothing found for HotSwap for package %s in outputs %s", jarDirectory, artifactsForLogging.get()));
                            }
                        }
                    });
                } else {
                    LOGGER.debug("Run hotswap after compile set to 'never'");
                }

            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.error("Exception occurred during building file to hotswap", e);
            }
        });
    }

    private void hotswapFile(Project project, String jarDirectory, File tempOutputDir) {
        Map<String, HotSwapFile> hotSwapFileMap = new HashMap<>();
        for (File file : tempOutputDir.listFiles()) {
            String fileName = file.getName();
            String name = jarDirectory.replace(Paths.DELIM, '.') +
                    (fileName.endsWith(".class") ?
                            fileName.substring(0, fileName.indexOf(".class")) :
                            fileName);
            hotSwapFileMap.put(name, new HotSwapFile(file));
        }

        List<DebuggerSession> sessions = DebuggerManagerEx.getInstanceEx(project).getSessions().stream()
                        .filter(HotSwapUIImpl::canHotSwap)
                                .collect(Collectors.toList());
        ApplicationManager.getApplication().invokeLater(() -> {
            final Collection<DebuggerSession> sessionsToHotswap;
            if (DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE.equals(DebuggerSettings.RUN_HOTSWAP_ASK)) {
                RunHotswapDialog runHotswapDialog = new RunHotswapDialog(project, sessions, false);
                if (!runHotswapDialog.showAndGet()) {
                    return;
                }

                sessionsToHotswap = runHotswapDialog.getSessionsToReload();
            } else {
                sessionsToHotswap = sessions;
            }

            HotSwapProgressImpl progress = new HotSwapProgressImpl(project);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    Map<DebuggerSession, Map<String, HotSwapFile>> sessionMap = sessionsToHotswap.stream()
                            .collect(Collectors.toMap(session -> session, session -> hotSwapFileMap));
                    ProgressManager.getInstance().runProcess(() -> {
                        HotSwapManager.reloadModifiedClasses(sessionMap, progress);
                    }, progress.getProgressIndicator());
                } finally {
                    progress.finished();
                    tempOutputDir.delete();
                }
            });
        });
    }

    private void findAndCopyOutputFile(VirtualFile sourceFile, File tempClassDir, String jarDirectory,
                                       BlazeBuildOutputs outputs) {
        String filenameWithoutExtension = sourceFile.getName().substring(0, sourceFile.getName().lastIndexOf('.'));

        outputs.artifacts.values().parallelStream()
                .filter(value -> value.artifact instanceof LocalFileOutputArtifact && value.artifact.getRelativePath().endsWith(".jar"))
                .map(value -> (LocalFileOutputArtifact) value.artifact)
                .forEach(artifact -> {
                    ProgressManager.checkCanceled();
                    try (JarInputStream jis = new JarInputStream(new FileInputStream(artifact.getFile()))) {
                        ZipEntry entry;
                        while ((entry = jis.getNextEntry()) != null) {
                            if (entry.isDirectory() && entry.getName().equals(jarDirectory)) {
                                LOGGER.info(String.format("Found entries for target package %s in %s", jarDirectory, entry.getName()));
                                int scannedEntries = 0;
                                int foundEntries = 0;
                                while ((entry = jis.getNextEntry()) != null && entry.getName().startsWith(jarDirectory)) {
                                    scannedEntries++;
                                    if (!entry.isDirectory()) {
                                        String entryFileName = entry.getName().substring(entry.getName().lastIndexOf(Paths.DELIM) + 1);
                                        String entryFileNameWithoutExtension = entryFileName.contains(".") ? entryFileName.substring(0, entryFileName.lastIndexOf('.')) : entryFileName;
                                        if (entryFileNameWithoutExtension.startsWith(filenameWithoutExtension) &&
                                                (entryFileNameWithoutExtension.length() == filenameWithoutExtension.length() ||
                                                        INNER_CLASS_PATTERN.matcher(entryFileNameWithoutExtension.substring(filenameWithoutExtension.length())).matches())) {
                                            LOGGER.debug(String.format("Adding %s to HotSwap list", entryFileName));
                                            File out = new File(tempClassDir, entryFileName);
                                            Files.copy(jis, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                            foundEntries++;
                                        }
                                    }
                                }
                                LOGGER.info(String.format("Scanned %d entries for %s and found %d for HotSwap", scannedEntries, jarDirectory, foundEntries));
                                break;
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.error("Failed scanning and extracting matching files from jars to hotswap", e);
                    }
                });
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        delegate.update(e);
        if (e.getPresentation().isEnabled()) {
            DebuggerSession session =
                    DebuggerManagerEx.getInstanceEx(e.getProject()).getContext().getDebuggerSession();

            VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (session != null && vf != null) {
                JavaDebugProcess process = session.getProcess().getXdebugProcess();
                if (process != null) {
                    ExecutionEnvironment env = ((XDebugSessionImpl) process.getSession()).getExecutionEnvironment();
                    if (env != null) {
                        RunProfile runProfile = env.getRunProfile();
                        //This action is not meant to replace the BlazeHotswapAction so we disable it for such cases
                        if (runProfile instanceof BlazeCommandRunConfiguration) {
                            e.getPresentation().setEnabled(false);
                            return;
                        }
                    }
                }

                ProjectTargetManager.SyncStatus status = ProjectTargetManager.getInstance(e.getProject()).getSyncStatus(vf.toNioPath().toFile());
                VirtualFile sourceRoot = ProjectFileIndex.getInstance(e.getProject()).getSourceRootForFile(vf);
                e.getPresentation().setEnabled(sourceRoot != null && status == ProjectTargetManager.SyncStatus.SYNCED);
            } else {
                e.getPresentation().setEnabled(false);
            }
        }
    }
}
