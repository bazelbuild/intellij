package com.google.idea.blaze.python.run;

import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

public interface BlazePyExecutableOutputFinder {

  static final ExtensionPointName<BlazePyExecutableOutputFinder> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazePyExecutableOutputFinder");

  @Nullable
  public File findExecutableOutput(Label target, List<File> outputs);

}
