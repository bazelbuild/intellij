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

**Important:** Always run tests against BOTH supported IDE versions to ensure compatibility:
- `clion-oss-oldest-stable`
- `clion-oss-latest-stable`

Run unit tests for a specific module:
```bash
bazel test //base:unit_tests --define=ij_product=clion-oss-latest-stable
bazel test //base:unit_tests --define=ij_product=clion-oss-oldest-stable
```

Run integration tests:
```bash
bazel test //base:integration_tests --define=ij_product=clion-oss-latest-stable
bazel test //base:integration_tests --define=ij_product=clion-oss-oldest-stable
```

Run all tests for a language plugin:
```bash
bazel test //cpp/... --define=ij_product=clion-oss-latest-stable
bazel test //cpp/... --define=ij_product=clion-oss-oldest-stable
```

Run aspect tests:
```bash
bazel test //aspect/testing:aspect_tests --define=ij_product=clion-oss-latest-stable
```

### Test Structure

Tests are organized into three categories:
- `unittests/` - Fast, isolated unit tests
- `integrationtests/` - Tests requiring a headless IntelliJ instance
- `utils/` - Test utilities and test data

## Development Setup

### Importing the Project

1. Install the Bazel plugin in your IntelliJ IDE
2. Import using `ijwb/ijwb.bazelproject` (or create your own `.bazelproject` file)
3. The project view file controls which directories and targets are imported

### Running a Development Instance

See `DEV_IDE_SETUP.md` for detailed instructions on setting up IntelliJ to debug the plugin.

Key steps:
1. Install DevKit plugin (IntelliJ 2023.3+)
2. Download and configure the JetBrains Runtime SDK
3. Create a "Bazel IntelliJ Plugin" run configuration
4. Configure the Plugin SDK to point to your JetBrains Runtime

### Project View Files

`.bazelproject` files control what gets imported. Key sections:
- `directories:` - Which directories to include/exclude (prefix with `-` to exclude)
- `targets:` - Which Bazel targets to build
- `build_flags:` - Bazel flags to use (including `--define=ij_product=...`)
- `workspace_type: intellij_plugin` - Marks this as a plugin project
- `use_query_sync: true/false` - Enable experimental Query Sync mode

## Architecture

### Plugin Structure

The plugin is organized as:
- **`/base`** - Core plugin machinery (sync, project import, run configurations, etc.)
- **Language plugins** - `/cpp`, `/python`, `/java`, `/golang`, `/dart`, `/javascript`, `/skylark`
- **Product bundles** - `/clwb` (CLion), `/ijwb` (IntelliJ)
- **Supporting dirs** - `/aspect`, `/sdkcompat`, `/testing`, `/querysync`

Each language plugin follows this structure:
```
language/
├── BUILD                    # Single BUILD file per plugin
├── src/
│   ├── META-INF/           # Plugin XML config files
│   └── com/google/...      # Source code
└── tests/
    ├── unittests/          # Fast unit tests
    └── integrationtests/   # Tests requiring IDE instance
```

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

#### Query Sync (Experimental)

An alternative, faster sync mode that uses Bazel queries instead of aspects. Enable with `use_query_sync: true` in `.bazelproject`. Currently supports Java, Kotlin, and C++.

Two actions in Query Sync mode:
- **Sync** - Fast sync for basic navigation
- **Enable Analysis** - Deeper analysis for full IDE features with external deps

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

## Important Directories

- `/aspect` - Starlark aspect code that traverses Bazel build during sync
- `/build_defs` - Bazel build definitions and `ij_product` version mappings
- `/examples` - Self-contained example projects for manual testing
- `/intellij_platform_sdk` - BUILD files for IntelliJ Platform SDK dependencies
- `/querysync` - Experimental faster sync using Bazel queries
- `/sdkcompat` - Multi-version IntelliJ SDK compatibility layer
- `/testing` - Test utilities and test runner infrastructure
- `/tools` - Build and maintenance tools

## Contributing

### Before Contributing

- Sign the Google Individual Contributor License Agreement
- Discuss larger changes via issue tracker first
- Focus on currently supported IDE versions (see `intellij_platform_sdk/build_defs.bzl`)

### SDK Compatibility Contributions

When supporting new IDE versions:
- Keep PRs small and focused on specific incompatible changes
- First try to make code work across versions directly (use newer APIs available in all versions)
- For non-trivial changes, add compat code in `/sdkcompat`
- Comment compat changes with `#api{VERSION}`
- Ensure changes work across all supported versions (presubmit tests will verify)

### Code Review Notes

- Stylistic/refactoring changes are not accepted
- Changes must target currently supported IDE versions
- Large patches require clear value for all users

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
