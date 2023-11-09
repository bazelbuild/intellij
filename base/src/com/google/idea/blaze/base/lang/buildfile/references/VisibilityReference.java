package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.lang.buildfile.completion.FilePathLookupElement;
import com.google.idea.blaze.base.lang.buildfile.completion.LabelRuleLookupElement;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import java.util.ArrayList;
import java.util.List;

public class VisibilityReference extends LabelReference {

  private static final ImmutableSet<String> PSEUDO_VISIBILITIES =
      ImmutableSet.of("__pkg__", "__subpackages__");

  public VisibilityReference(StringLiteral element, boolean soft) {
    super(element, soft);
  }

  @Override
  public Object[] getVariants() {
    Object[] variants = super.getVariants();
    ArrayList<LookupElement> results = new ArrayList<>();
    for (Object v : variants) {
      if (v instanceof FilePathLookupElement) {
        FilePathLookupElement le = ((FilePathLookupElement) v);
        if (le.getIsDirectory()) {
          results.add(
              LookupElementBuilder.create("\"" + le.getLabel() + "/")
                  .withPresentableText(le.getLabel())
                  .withIcon(AllIcons.Nodes.Variable));
          results.addAll(createPseudoVisibilitiesForPackage(le.getLabel()));
        }

      } else if (v instanceof LabelRuleLookupElement) {
        LabelRuleLookupElement le = ((LabelRuleLookupElement) v);
        if (le.getRuleType().equals("package_group")) {
          results.add(((LookupElement) le));
          Label lbl = Label.createIfValid(le.getLabel());
          if (lbl != null) {
            String pkg = "//" + lbl.blazePackage();
            results.addAll(createPseudoVisibilitiesForPackage(pkg));
          }
        }
      }
    }

    ArrayList<String> globalVisibilities = new ArrayList<>();
    globalVisibilities.add("//visibility:public");
    globalVisibilities.add("//visibility:private");

    for (String v : globalVisibilities) {
      results.add(
          LookupElementBuilder.create("\"" + v)
              .withIcon(AllIcons.Nodes.Variable)
              .withPresentableText(v));
      results.add(
          LookupElementBuilder.create("'" + v)
              .withIcon(AllIcons.Nodes.Variable)
              .withPresentableText(v));
    }

    return results.toArray();
  }

  private List<LookupElement> createPseudoVisibilitiesForPackage(String pkg) {
    List<LookupElement> result = new ArrayList<>(PSEUDO_VISIBILITIES.size() * 2);
    for (String pv : PSEUDO_VISIBILITIES) {
      result.add(
              LookupElementBuilder.create("\"" + pkg + ":" + pv)
                      .withPresentableText(pkg + ":" + pv)
                      .withIcon(AllIcons.Nodes.Variable));
      result.add(
              LookupElementBuilder.create("'" + pkg + ":" + pv)
                      .withPresentableText(pkg + ":" + pv)
                      .withIcon(AllIcons.Nodes.Variable));
    }
    return result;
  }
}
