package com.google.idea.blaze.gazelle;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import org.jetbrains.annotations.Nullable;

/** Section for project-specific gazelle configuration. */
public class GazelleSection {

  public static final SectionKey<Label, ScalarSection<Label>> KEY = SectionKey.of("gazelle_target");
  public static final SectionParser PARSER = new GazelleSectionParser();

  private static class GazelleSectionParser extends ScalarSectionParser<Label> {

    public GazelleSectionParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected Label parseItem(ProjectViewParser parser, ParseContext parseContext, String text) {
      if (text == null) {
        return null;
      }
      String error = Label.validate(text);
      if (error != null) {
        parseContext.addError(error);
        return null;
      }
      return Label.create(text);
    }

    @Override
    protected void printItem(StringBuilder sb, Label value) {
      sb.append(value.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Label;
    }

    @Override
    public String quickDocs() {
      return "Gazelle target used to refresh the project. If not specified, gazelle will not run on"
          + " sync.";
    }
  }
}
