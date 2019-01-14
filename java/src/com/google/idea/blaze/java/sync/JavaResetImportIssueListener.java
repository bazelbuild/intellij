package com.google.idea.blaze.java.sync;

import com.google.idea.blaze.base.ui.problems.ImportProblemContainerServiceBase;
import com.google.idea.blaze.base.ui.problems.ResetImportIssueNotifier;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;

import static com.google.idea.blaze.base.ui.problems.ResetImportIssueNotifier.RESET_IMPORT_ISSUE_NOTIFIER_TOPIC;


public class JavaResetImportIssueListener implements ProjectComponent {

    private MessageBus messageBus ;
    private ImportProblemContainerServiceBase importProblemContainerService = ServiceManager.getService(JavaLikeImportProblemContainerService.class);

    public JavaResetImportIssueListener(Project project) {
        messageBus = project.getMessageBus();


        messageBus.connect().subscribe(RESET_IMPORT_ISSUE_NOTIFIER_TOPIC, new ResetImportIssueNotifier() {
            @Override
            public void resetIssues() {
                importProblemContainerService.resetIssues();
            }
        });

    }
}
