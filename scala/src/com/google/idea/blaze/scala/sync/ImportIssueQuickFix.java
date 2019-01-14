package com.google.idea.blaze.scala.sync;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.ui.problems.ImportIssue;
import com.google.idea.blaze.base.ui.problems.ImportIssueQuickFixBase;
import com.google.idea.blaze.base.ui.problems.ImportProblemContainerServiceBase;
import com.intellij.openapi.components.ServiceManager;

import java.util.List;

public class ImportIssueQuickFix extends ImportIssueQuickFixBase {

    public ImportIssueQuickFix(String key, ImportIssue importIssue, List<Label> importClassTargets, Label currentClassTarget) {
        super(key, importIssue, importClassTargets, currentClassTarget);
    }

    @Override
    public ImportProblemContainerServiceBase getImportIssueContainerService() {
        return ServiceManager.getService(ScalaLikeImportProblemContainerService.class);
    }
}
