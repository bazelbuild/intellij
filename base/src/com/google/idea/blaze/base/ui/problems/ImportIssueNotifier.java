package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;

import static com.google.idea.blaze.base.ui.problems.ImportIssueConstants.IMPORT_ISSUE_NOTIFIER_TOPIC_NAME;

public interface ImportIssueNotifier {
    Topic<ImportIssueNotifier> IMPORT_ISSUE_NOTIFIER_TOPIC = Topic.create(IMPORT_ISSUE_NOTIFIER_TOPIC_NAME, ImportIssueNotifier.class);

    void notify(IssueOutput issue, PsiFile file, ImportIssueType importIssueType);

}
