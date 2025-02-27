package com.google.idea.blaze.python.run;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlazePyDebugHelperImpl implements BlazePyDebugHelper {
    private static final Logger logger = Logger.getInstance(BlazePyDebugHelperImpl.class);

    public static final Map<TargetExpression, Path> knownExecRoots = new HashMap<>();


    @Override
    public void patchBlazeDebugCommandline(Project project, TargetExpression target, GeneralCommandLine commandLine) {
        logger.info(String.format("The target executable map contains %d keys", knownExecRoots.size()));
        if(!knownExecRoots.containsKey(target)) {
            logger.warn("No cached executable path for " + target);
            return;
        }
        try {
            Label label = Label.create(target.toString());
            WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
            Path bazelBin = workspaceRoot.absolutePathFor("bazel-bin");
            File sourcePackageRoot = WorkspaceHelper.resolveBlazePackage(project, label);
            Objects.requireNonNull(sourcePackageRoot, "Could not resolve the package root for " + target);
            Path relativePkg = workspaceRoot.workspacePathFor(sourcePackageRoot).asPath();
            String workspaceName = parseWorkspaceName(knownExecRoots.get(target));
            Path actualBinPath = bazelBin.resolve(relativePkg)
                    .resolve(label.targetName() + ".runfiles")
                    .resolve(workspaceName).resolve(relativePkg);
            logger.info(String.format("Target %s: mapping %s to %s", target, sourcePackageRoot, actualBinPath));
            commandLine.withEnvironment("PATHS_FROM_ECLIPSE_TO_PYTHON", String.format("[[\"%s\", \"%s\"]]", sourcePackageRoot, actualBinPath));
        } catch (RuntimeException e) {
            logger.error("Failed to calculate the debugger path mapping for " + target, e);
        }
    }

    private String parseWorkspaceName(Path executableFile) {
        logger.info("Attempting to parse path: " + executableFile);
        Pattern p = Pattern.compile("^/(?:[\\w-.]+/)+execroot/([\\w-]+)/bazel-out/[\\w-]+/bin/([\\w-]+(?:/[\\w-]+)*)/[\\w-]+$");
        Matcher m = p.matcher(executableFile.toString());
        if(m.matches()) {
            return m.group(1);
        }
        throw new IllegalStateException("Could not parse the executable path: " + executableFile);
    }

}
