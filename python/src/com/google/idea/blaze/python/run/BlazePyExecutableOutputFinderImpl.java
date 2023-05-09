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
      String fileName = file.getName();
      if (fileName.equals(name)) {
        return file;
      }
      int exeIndex = fileName.lastIndexOf(".exe");
      if (exeIndex > 0 && fileName.substring(0, exeIndex).equals(name)) {
        return file;
      }
    }
    return null;
  }
}
