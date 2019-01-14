package com.google.idea.blaze.base.ui.problems;

import com.intellij.util.messages.Topic;

import static com.google.idea.blaze.base.ui.problems.ImportIssueConstants.RESET_IMPORT_ISSUE_NOTIFIER_TOPIC_NAME;

public interface ResetImportIssueNotifier {
    Topic<ResetImportIssueNotifier> RESET_IMPORT_ISSUE_NOTIFIER_TOPIC = Topic.create(RESET_IMPORT_ISSUE_NOTIFIER_TOPIC_NAME, ResetImportIssueNotifier.class);

    void resetIssues();

}
