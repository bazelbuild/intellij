package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public abstract class TypeScriptConfigServiceAdapter implements TypeScriptConfigService {

    public abstract TypeScriptConfigService getImpl();
}
