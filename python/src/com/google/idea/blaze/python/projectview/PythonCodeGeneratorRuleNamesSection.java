package com.google.idea.blaze.python.projectview;

import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.codegenerator.CodeGeneratorRuleNameHelper;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * The Python language support in the plugin allows for some Rule names to be specified as being
 * code-generators. This section can be used to specify that list of names.
 */

public class PythonCodeGeneratorRuleNamesSection {

  public static final SectionKey<String, ListSection<String>> KEY = SectionKey.of("python_code_generator_rule_names");
  public static final SectionParser PARSER = new PythonCodeGeneratorRuleNamesSection.PythonCodeGeneratorRuleNamesSectionParser();

  static class PythonCodeGeneratorRuleNamesSectionParser extends ListSectionParser<String> {
    PythonCodeGeneratorRuleNamesSectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected String parseItem(ProjectViewParser parser, ParseContext parseContext) {
      String ruleName = parseContext.current().text;

      if (!CodeGeneratorRuleNameHelper.isValidRuleName(ruleName)) {
        parseContext.addError(
            String.format("[%s] contains an invalid rule name [%s]", getName(), ruleName));
        return null;
      }

      return ruleName;
    }

    @Override
    protected void printItem(String item, StringBuilder sb) {
      sb.append(item);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return String.format(
          "A list of %s rule names that are taken to be code generators for the Python language.",
          Blaze.guessBuildSystemName());
    }
  }
}
