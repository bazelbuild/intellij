# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the Bazel for CLion plugin (and historically IntelliJ) repository. The plugin is maintained by JetBrains as of July 2025. It provides Bazel build system integration for JetBrains IDEs.

**Important:** The CLion plugin is built from the `master` branch. The IntelliJ plugin is built from the `ijwb` branch (maintenance-only). The Android Studio plugin is maintained in AOSP.

## Build Commands

### Building the Plugin

Build the plugin for CLion:
```bash
bazel build //clwb:clwb_bazel_zip --define=ij_product=clion-oss-latest-stable
```

The plugin zip will be created at `bazel-bin/clwb/clwb_bazel.zip`.

### Product Version Aliases

The `ij_product` flag controls which IDE version to build for:
- `clion-oss-oldest-stable` - Oldest supported IDE version
- `clion-oss-latest-stable` - Latest supported IDE version
- `clion-oss-under-dev` - Upcoming IDE version being worked on
- Direct versions: `clion-2025.2`, `clion-2025.3`, etc.

Version mappings are defined in `intellij_platform_sdk/build_defs.bzl`.

### Running Tests

**Important:** Always run tests against ALL supported IDE versions to ensure compatibility:
- `clion-oss-oldest-stable`
- `clion-oss-latest-stable`
- `clion-oss-under-dev`

Run unit tests for a specific module:
```bash
bazel test //base:unit_tests --define=ij_product=clion-oss-latest-stable
bazel test //base:unit_tests --define=ij_product=clion-oss-oldest-stable
bazel test //base:unit_tests --define=ij_product=clion-oss-under-dev
```

Run integration tests:
```bash
bazel test //base:integration_tests --define=ij_product=clion-oss-latest-stable
bazel test //base:integration_tests --define=ij_product=clion-oss-oldest-stable
bazel test //base:integration_tests --define=ij_product=clion-oss-under-dev
```

Run all tests for a language plugin:
```bash
bazel test //cpp/... --define=ij_product=clion-oss-latest-stable
bazel test //cpp/... --define=ij_product=clion-oss-oldest-stable
bazel test //cpp/... --define=ij_product=clion-oss-under-dev
```

Run aspect tests:
```bash
bazel test //aspect/... --define=ij_product=clion-oss-latest-stable
bazel test //aspect/... --define=ij_product=clion-oss-oldest-stable
bazel test //aspect/... --define=ij_product=clion-oss-under-dev
```

### Test Structure

Tests are organized into three categories:
- `unittests/` - Fast, isolated unit tests
- `integrationtests/` - Still relatively fast, used for interacting with IntelliJ platform
- `headlesstests/` - Slow, require headless CLion, used for testing the entire sync process 

### Project View Files

`.bazelproject` files control what gets imported. Key sections:
- `directories:` - Which directories to include/exclude (prefix with `-` to exclude)
- `targets:` - Which Bazel targets to build
- `build_flags:` - Bazel flags to use (including `--define=ij_product=...`)
- `workspace_type: intellij_plugin` - Marks this as a plugin project

## Architecture

### Plugin Structure

The plugin is organized as:
- **`/base`** - Core plugin machinery (sync, project import, run configurations, etc.)
- **Language plugins** - `/cpp`, `/python`, `/javascript`, `/skylark`
- **Product bundles** - `/clwb` (CLion), `/ijwb` (IntelliJ, deprecated)
- **Supporting dirs** - `/aspect`, `/sdkcompat`, `/testing`

#### Legacy Layout (Monolithic)

Each language plugin historically followed this structure:
```
language/
├── BUILD                    # Single BUILD file per plugin (600+ lines)
├── src/
│   ├── META-INF/           # Plugin XML config files
│   └── com/google/...      # Source code
└── tests/
    ├── unittests/          # Fast unit tests
    └── integrationtests/   # Tests requiring IDE instance
```

**Examples:** `/base/BUILD` (628 lines), `/cpp/BUILD`, `/python/BUILD`

This monolithic approach has all targets for a plugin in one BUILD file, making it harder to maintain as the plugin grows.

#### Modern Layout (Fine-Grained)

New code follows a more Bazel-like, fine-grained project structure:
```
module/
├── BUILD                                    # Aggregator with exports
├── src/com/google/idea/blaze/
│   ├── submodule1/
│   │   ├── BUILD                           # Individual library
│   │   └── *.java
│   └── submodule2/
│       ├── BUILD                           # Individual library
│       └── *.java
└── tests/unittests/com/google/idea/blaze/
    ├── BUILD                               # Every test file is a Bazel target
    └── *Test.java
```

This approach provides:
- Better dependency visibility and control
- Easier to understand module boundaries
- More parallelizable builds
- Cleaner refactoring paths

### Key Concepts

#### Sync Process

"Syncing" translates the Bazel build into IntelliJ's project model:

1. **Build Phase**: Run `bazel build` with the aspect in `/aspect`
   - The aspect traverses targets and outputs `*.intellij-info.txt` proto files
   - These protos contain information needed by the IDE (generated files, dependencies, etc.)

2. **Update Phase**: Read the protos and:
   - Generate a `TargetMap` (in-memory representation of the Bazel build)
   - Translate into IntelliJ modules and libraries
   - Update project structure

3. **Index Phase**: IntelliJ re-indexes changed symbols

Key classes:
- `BlazeSyncManager` - Entry point for sync operations
- `SyncPhaseCoordinator` - Orchestrates the sync phases
- `SyncListener` - Extension point for reacting to sync events (hooks: `onSyncStart`, `onSyncComplete`, `afterSync`)
- `BlazeSyncPlugin` - Main interface for language plugins to interact with sync

#### Target System

- `TargetMap` - In-memory representation of Bazel build, accessible via `BlazeProjectDataManager`
- `TargetKey` - Keys into `TargetMap` (represents a Bazel target)
- `TargetIdeInfo` - Values in `TargetMap` (info gathered from a target: sources, deps, etc.)
- `SourceToTargetMap` - Maps source files to Bazel targets
- `Kind` - How the plugin represents Bazel rules (e.g., `py_binary`, `go_library`)
  - Sub-plugins register `Kind`s via `Kind.Provider`
  - Unregistered rules are ignored by the plugin

#### Configuration

- `ProjectView` - In-memory representation of a `.bazelproject` file
- `ProjectViewSet` - Hierarchy of composed `ProjectView`s (via `import` statements)
- `UserSettings` - Global settings for the plugin (accessible via `Cmd+, -> Bazel`)

#### Run Configurations

Flow: Context → Producer → RunConfiguration → Handler → Runner → Execution

- `RunConfigurationProducer` - Creates run configurations from contexts (gutter icons in BUILD files, test classes, main functions)
- `RunConfiguration` - Stateful representation of what to run
- `BlazeCommandRunConfigurationHandler` - Holds state and creates runners
- `BlazeCommandRunConfigurationRunner` - Crafts the command line to execute

See `run/` subdirectories in language plugins (e.g., `/golang/src/com/g.../golang/run`).

### SDK Compatibility (`/sdkcompat`)

The plugin supports multiple IntelliJ versions simultaneously. SDK compatibility code lives in:
- `/sdkcompat` - Production compatibility code
- `/testing/testcompat` - Test-only compatibility code

Each directory contains version-specific subdirectories (e.g., `v252/`, `v253/`).

Three patterns for handling incompatibilities:
1. **Compat** - Static utility methods wrapping changed APIs
2. **Adapter** - New class extending changed superclass, plugin extends the adapter
3. **Wrapper** - Wraps interfaces to provide old/new interface based on SDK version

**Important rules:**
- All compat changes must be commented with `#api{VERSION}` (e.g., `#api252`)
- This marks the last API version requiring the code
- Compat classes must never import plugin code
- Keep compat code minimal

### Extension Points

The base plugin defines extension points that language plugins can implement:
- `SyncPlugin` - Hook into sync process
- `SyncListener` - React to sync events
- `RunConfigurationProducer` - Define how to run/test targets
- `Kind.Provider` - Register new Bazel rule types

Check `base/src/META-INF/blaze-base.xml` and language plugin XML files for extension points.

### IntelliJ SDK Concepts

Familiarize yourself with these IntelliJ Platform concepts:
- [Extension Points](https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html)
- [Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)
- [Listeners](https://plugins.jetbrains.com/docs/intellij/plugin-listeners.html)
- [Actions](https://plugins.jetbrains.com/docs/intellij/basic-action-system.html)
- [Run Configurations](https://plugins.jetbrains.com/docs/intellij/run-configurations.html)

## BUILD File Layout Migration

The project is migrating from **monolithic** BUILD files (one large file per plugin) to **fine-grained** BUILD files (distributed across the codebase following package structure). This follows Bazel best practices and improves maintainability.

### When to Use Each Layout

**Use fine-grained layout for:**
- New modules or subsystems
- Shared libraries (like `/shared`, `/common`)
- Code that will be reused across plugins
- Modules with clear boundaries

### Migration Guide

#### Create Fine-Grained BUILD Files

For each logical module, create a BUILD file in its package directory.

**Example: Creating a fine-grained library**

Before (monolithic `/base/BUILD`):
```starlark
kt_jvm_library(
    name = "base",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    deps = [
        # 50+ dependencies...
    ],
)
```

After (fine-grained `/base/src/com/google/idea/blaze/base/command/buildresult/bepparser/BUILD`):
```starlark
load("//build_defs:build_defs.bzl", "intellij_plugin_library")

intellij_plugin_library(
    name = "bepparser",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    deps = [
        "//common/experiments",
        "//intellij_platform_sdk:plugin_api",
        "//proto:proto_deps",
        "//shared:artifact",
        "//shared:exception",
        "@com_google_guava_guava//jar",
    ],
)
```

**Note:** Always prefer `intellij_plugin_library` over manually creating `kt_jvm_library` or `java_library` targets. The rule handles compilation internally and provides proper plugin integration.

#### Use `intellij_plugin_library` for Plugin Code

**Always prefer `intellij_plugin_library`** over manually creating `kt_jvm_library` or `java_library` targets. The rule handles compilation internally and provides proper plugin integration.

**Preferred approach:**
```starlark
load("//build_defs:build_defs.bzl", "intellij_plugin_library")

intellij_plugin_library(
    name = "mymodule",
    srcs = glob(["*.kt"]),  # Rule handles Kotlin compilation internally
    plugin_xmls = ["META-INF/mymodule.xml"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
        "//shared",
    ],
)
```

**When you don't have sources** (pure aggregator):
```starlark
intellij_plugin_library(
    name = "mymodule",
    plugin_xmls = ["META-INF/mymodule.xml"],
    deps = [
        "//base",
        "//shared",
    ],
)
```

The `intellij_plugin_library` rule:
- Automatically compiles sources if `srcs` is provided (creates internal `kt_jvm_library`)
- Merges `plugin_xmls` from all dependencies
- Handles optional plugin XMLs
- Manages resources and data files
- Provides `IntellijPluginLibraryInfo` provider for plugin assembly
- Supports inter-plugin dependencies
- Exposes `JavaInfo` provider for downstream consumers

### Best Practices

1. **Prefer `intellij_plugin_library`**: For plugin code, always use `intellij_plugin_library` instead of manually creating `kt_jvm_library`/`java_library` targets. It handles compilation and plugin integration automatically.
   - **Exception**: Pure shared utilities with no IntelliJ Platform dependencies (like `/shared`) can use `java_library`
   - **Rule of thumb**: If it depends on `//intellij_platform_sdk:plugin_api` or might have plugin XMLs, use `intellij_plugin_library`
2. **Start Small**: Migrate one logical module at a time
3. **Use Precise Dependencies**: Depend on specific sub-modules, not entire plugins
4. **Follow Package Structure**: BUILD files should align with Java package structure
5. **One Library Per BUILD**: Each BUILD file should define one primary library (plus tests/utilities)
6. **Use Visibility Controls**: Set appropriate visibility for each target
   - `visibility = ["//visibility:private"]` - Only usable in same BUILD file
   - `visibility = ["//base:__subpackages__"]` - Usable within base plugin

### Common Patterns

#### Pattern 1: Plugin Library with Sources
```starlark
intellij_plugin_library(
    name = "mylib",
    srcs = glob(["*.java"]),  # Can also use *.kt for Kotlin
    visibility = ["//base:__subpackages__"],
    deps = [
        "//shared",
        "@com_google_guava_guava//jar",
    ],
)
```

#### Pattern 2: Plugin Library with Plugin XML
```starlark
intellij_plugin_library(
    name = "mylib",
    srcs = glob(["*.kt"]),
    plugin_xmls = ["META-INF/mylib-plugin.xml"],
    deps = [
        "//intellij_platform_sdk:plugin_api",
        "//shared",
    ],
)
```

#### Pattern 3: Plugin Library with Resources
```starlark
intellij_plugin_library(
    name = "mylib",
    srcs = glob(["*.kt"]),
    resources = {
        "icons/myicon.svg": "//resources/icons:myicon",
    },
    deps = ["//shared"],
)
```

#### Pattern 4: Plugin Library with Data Files
```starlark
intellij_plugin_library(
    name = "mylib",
    srcs = glob(["*.kt"]),
    data = {
        "templates/default.xml": "//resources/templates:default",
    },
    deps = ["//shared"],
)
```

#### Pattern 5: Pure Aggregator (No Sources)
```starlark
intellij_plugin_library(
    name = "mylib",
    plugin_xmls = ["META-INF/mylib-plugin.xml"],
    deps = [
        "//base/src/com/google/idea/blaze/base/submodule1",
        "//base/src/com/google/idea/blaze/base/submodule2",
    ],
)
```

## Important Directories

### Plugin Directories

- `/aspect` - Starlark aspect code that traverses Bazel build during sync
- `/base` - Core plugin machinery (sync, project import, run configurations)
- `/build_defs` - Bazel build definitions and `ij_product` version mappings
- `/clwb` - CLion plugin bundle
- `/common` - Shared utilities and settings (fine-grained layout)
- `/cpp`, `/python`, `/javascript`, `/skylark` - Language-specific plugin implementations
- `/examples` - Self-contained example projects for manual testing
- `/intellij_platform_sdk` - BUILD files for IntelliJ Platform SDK dependencies
- `/sdkcompat` - Multi-version IntelliJ SDK compatibility layer
- `/shared` - Shared libraries and utilities (fine-grained layout)
- `/testing` - Test utilities and test runner infrastructure
- `/tools` - Build and maintenance tools

### Reference Directories (Read-Only)

The workspace may include reference directories from the IntelliJ IDEA monorepo. These are **for context only** - use them to understand IntelliJ Platform APIs, research implementation patterns, or learn how core IDE features work. **Never modify files in these directories.**

- `ultimate/` - IntelliJ IDEA Ultimate monorepo (full source, including proprietary modules)
- `community/` - For external contributors, this directory contains the open-source IntelliJ Community Edition

**When to use the IntelliJ monorepo:**
- Understanding how to use IntelliJ Platform APIs correctly
- Finding examples of extension point implementations
- Researching how core IDE features work (VFS, indexing, PSI, etc.)
- Debugging unexpected IDE behavior
- Understanding sdkcompat requirements for new IDE versions

**How to search the monorepo:**
```bash
# From the plugin directory, search IntelliJ source
cd /path/to/ultimate
# or for external contributors:
cd /path/to/community

# Find usage examples of an API
git grep "SomeIntelliJClass"

# Find extension point definitions
find . -name "*.xml" -exec grep -l "extensionPoint.*myExtensionPoint" {} \;
```

**Important:** Always implement solutions in the plugin repository, not in the IntelliJ monorepo. The monorepo is read-only reference material.

## Contributing

### Using the IntelliJ Monorepo for Research

When implementing features or fixing bugs, leverage the IntelliJ IDEA source code (located at <path-to-intellij-monorepo>/ultimate or community/ for external contributors) as reference:

1. **Before implementing a feature**: Search the IntelliJ source for similar implementations
2. **When using unfamiliar APIs**: Find real usage examples in the IDE source
3. **For extension points**: Look up extension point definitions and implementations
4. **When debugging**: Compare plugin behavior with how the core IDE handles similar cases

**Example workflow:**
```bash
# Find how IntelliJ implements run configurations
cd /path/to/ultimate
git grep "class.*RunConfiguration" -- "*.java" "*.kt"
# ... make your changes ...
```

**Remember:** The IntelliJ monorepo is **read-only reference material**. All implementations go in the plugin repository.

### SDK Compatibility Contributions

When supporting new IDE versions:
- Keep PRs small and focused on specific incompatible changes
- First try to make code work across versions directly (use newer APIs available in all versions)
- For non-trivial changes, add compat code in `/sdkcompat`
- Comment compat changes with `#api{VERSION}`
- Ensure changes work across all supported versions (presubmit tests will verify)
- Use the IntelliJ monorepo to understand API changes between versions

## Acronyms

- **ijwb** - IntelliJ With Bazel
- **clwb** - CLion With Bazel
- **aswb** - Android Studio With Bazel (now in AOSP)

## Resources

- [Official Plugin Documentation](https://github.com/bazelbuild/intellij/blob/master/docs/index.md)
- [Architecture Deep Dive](ARCHITECTURE.md)
- [IntelliJ Plugin SDK Docs](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [JetBrains Marketplace - Bazel Plugin](https://plugins.jetbrains.com/plugin/8609-bazel)
- [SIG Bazel IntelliJ Charter](https://github.com/bazelbuild/community/blob/main/sigs/bazel-intellij/CHARTER.md)
