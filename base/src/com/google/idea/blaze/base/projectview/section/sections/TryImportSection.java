package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;

import javax.annotation.Nullable;

public class TryImportSection extends ImportSection {

    public static final SectionKey<WorkspacePath, ScalarSection<WorkspacePath>> KEY =
            SectionKey.of("try-import");

    public static final SectionParser PARSER = new TryImportSectionParser();

    private static class TryImportSectionParser extends ImportSectionParser {

        public TryImportSectionParser() {
            super(KEY, ' ');
        }

        @Nullable
        @Override
        protected WorkspacePath parseItem(
                ProjectViewParser parser, ParseContext parseContext, String text) {
            return parseItem(parser, parseContext, text, false);
        }
    }
}
