# Custom Rule Source Configuration

This document explains how to use the `CustomRuleSourceConfig` extension point to add support for custom Bazel rules that need special source attribute handling.

## Overview

The `CustomRuleSourceConfig` extension point allows plugins to specify which attributes contain sources for custom rule types. This is useful when you have custom Bazel rules that have srcs attributes beyond the `srcs` field.

## How It Works

The extension point generates aspect code that:
1. Collects files from specified attributes on your custom rules
2. Separates source files from generated files
3. Adds them to the appropriate language-specific IDE info (Go, Python, C++, etc.)
4. Updates output groups for sync and compilation

## Example: Custom Go Rules

Let's say you have a custom Bazel rule `cff` or Go code generation that use custom source attributes:

```starlark
# In your BUILD file
load("//rules:codepatch.bzl", "cff")

cff(
    name = "my_cff_target",
    srcs = ["base.go"],        # Standard sources
    cff_srcs = ["cff_src.go"],    # Custom cff-specific sources
)
```

### Step 1: Create Source Config Implementations

Create a class implementing `CustomRuleSourceConfig`:

```java
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.CustomRuleSourceConfig;
public class CffRuleSourceConfig implements CustomRuleSourceConfig {
  @Override
  public String getRuleKind() {
    return "cff";
  }

  @Override
  public LanguageClass getLanguageClass() {
    return LanguageClass.GO;
  }

  @Override
  public Collection<String> getSourceAttributes() {
    // Collect from both standard 'srcs' and custom 'cff_srcs' attributes
    return ImmutableList.of("srcs", "cff_srcs");
  }
}
```

### Step 2: Register Kind Providers

You'll also want to register these rule kinds so the IDE knows about them:

```java
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
public class CustomKindProvider implements Kind.Provider {
  @Override
  public ImmutableSet<Kind> getTargetKinds() {
    return ImmutableSet.of(
      Kind.Provider.create("cff", LanguageClass.GO, RuleType.LIBRARY),
    );
  }
}
```

### Step 3: Register Extensions in plugin.xml

```xml
<idea-plugin>
  <depends>com.google.idea.bazel.ijwb</depends>
  
  <extensions defaultExtensionNs="com.google.idea.blaze">
    <CustomRuleSourceConfig implementation="com.yourcompany.codepatch.CffRuleSourceConfig"/>
    <TargetKindProvider implementation="com.yourcompany.codepatch.CustomKindProvider"/>
  </extensions>
</idea-plugin>
```

### Step 4: Sync and Test

Install your plugin alongside the ijwb plugin and Sync.

## Supported Languages

The system automatically handles different language classes:

- **GO**: Creates `go_ide_info` with sources and generated files
- **PYTHON**: Creates `py_ide_info` with sources
- **C/CPP**: Creates `c_ide_info` with sources in rule context
- **Other languages**: Falls back to generic output groups


## Implementation Details

When you register a `CustomRuleSourceConfig`:

1. During sync, `AspectTemplateWriter` collects all registered configs
2. Generates `code_generator_info.bzl` with a `CUSTOM_RULE_SOURCES` struct
3. The aspect calls `collect_custom_rule_info()` for matching rules
4. Files are collected from specified attributes and added to IDE info
5. The IDE uses this info for navigation, completion, and other features
