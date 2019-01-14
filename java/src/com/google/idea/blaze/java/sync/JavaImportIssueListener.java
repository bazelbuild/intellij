package com.google.idea.blaze.java.sync;

import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.ui.problems.ImportIssueNotifier;
import com.google.idea.blaze.base.ui.problems.ImportIssueType;
import com.google.idea.blaze.base.ui.problems.ImportProblemContainerServiceBase;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;

import static com.google.idea.blaze.base.ui.problems.ImportIssueNotifier.IMPORT_ISSUE_NOTIFIER_TOPIC;


public class JavaImportIssueListener implements ProjectComponent {

    private MessageBus messageBus ;
    private ImportProblemContainerServiceBase importProblemContainerService = ServiceManager.getService(JavaLikeImportProblemContainerService.class);

    public JavaImportIssueListener(Project project) {
        messageBus = project.getMessageBus();
        messageBus.connect().subscribe(IMPORT_ISSUE_NOTIFIER_TOPIC, new ImportIssueNotifier() {
            @Override
            public void notify(IssueOutput issue, PsiFile file, ImportIssueType importIssueType) {
                String fileName = file.getContainingFile().getName();
                if(fileName.toLowerCase().endsWith("java")){
                    importProblemContainerService.setIssue(issue, file, importIssueType);
                }
            }
        });

    }
}
