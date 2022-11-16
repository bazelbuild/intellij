package com.google.idea.blaze.gazelle;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import java.util.Collection;

public class GazelleSyncPlugin implements BlazeSyncPlugin {
  public Collection<SectionParser> getSections() {
    return ImmutableList.of(GazelleSection.PARSER);
  }
}
