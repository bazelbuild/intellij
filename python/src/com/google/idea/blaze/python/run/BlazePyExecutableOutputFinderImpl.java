package com.google.idea.blaze.python.run;

import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.util.PathUtil;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

public class BlazePyExecutableOutputFinderImpl implements BlazePyExecutableOutputFinder {

  @Nullable
  @Override
  public File findExecutableOutput(Label target, List<File> outputs) {
    if (outputs.size() == 1) {
      return outputs.get(0);
    }
    String name = PathUtil.getFileName(target.targetName().toString());
    for (File file : outputs) {
      if (file.getName().equals(name)) {
        return file;
      }
    }
    return null;
  }
}
