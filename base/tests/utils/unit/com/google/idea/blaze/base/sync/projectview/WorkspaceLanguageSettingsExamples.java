package com.google.idea.blaze.base.sync.projectview;

import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;

/**
 * A collection of pre-built {@link WorkspaceLanguageSettings} which are frequently used in tests.
 */
public class WorkspaceLanguageSettingsExamples {
    public static final class SingleLanguage {
        public static final WorkspaceLanguageSettings GO = new WorkspaceLanguageSettings(
                WorkspaceType.GO, Sets.immutableEnumSet(LanguageClass.GO));
        public static final WorkspaceLanguageSettings JAVA = new WorkspaceLanguageSettings(
                WorkspaceType.JAVA, Sets.immutableEnumSet(LanguageClass.JAVA));
        public static final WorkspaceLanguageSettings JAVASCRIPT = new WorkspaceLanguageSettings(
                WorkspaceType.JAVASCRIPT, Sets.immutableEnumSet(LanguageClass.JAVASCRIPT));
    }
}
