# Loose notes about the plugin's architecture

## Sources Better Than This One
These are official sources in some form or another. They are better and more up to date than this doc, but not all answers are there:
- The [IntelliJ Plugin SDK Docs](https://plugins.jetbrains.com/docs/intellij/welcome.html): The official docs on how to extend IntelliJ. In particular, I strongly recommend you read these two documents start to finish before even opening IJ:
	- [Plugin Structure](https://plugins.jetbrains.com/docs/intellij/plugin-structure.html): An overview of how to structure a plugin, how to hook into IntelliJ's many APIs, and how to add custom functionality.
	- [Custom Language Support Tutorial](https://plugins.jetbrains.com/docs/intellij/custom-language-support-tutorial.html): How to add features for a fictitious language, step by step.
- [How to set up IntelliJ to develop the Bazel plugin](https://github.com/bazelbuild/intellij/blob/master/DEV_IDE_SETUP.md): Running a debug instance of IntelliJ so that you can set breakpoints.
- [The IntelliJ Community Edition OSS plugins](https://github.com/JetBrains/intellij-community/tree/master/plugins): IntelliJ Community Edition comes bundled with a bunch of plugins, written by JetBrains, that we can learn from. 
## Important Concepts You Should Know Before Continuing
These come up again and again in the codebase.

### Base IntelliJ SDK Concepts
- [Extension Points](https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html) (e.g. [`SourceFileFinder`](https://github.com/bazelbuild/intellij/blob/6da9ea8dc591c0dabdd174073c7dd54b82889da6/cpp/src/com/google/idea/blaze/cpp/SourceFileFinder.java#L23-L25))
- [Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html) (e.g. [`EventLoggingService`](https://github.com/bazelbuild/intellij/blob/6da9ea8dc591c0dabdd174073c7dd54b82889da6/base/src/com/google/idea/blaze/base/logging/EventLoggingService.java#L35-L40))
- [Listener](https://plugins.jetbrains.com/docs/intellij/plugin-listeners.html) (e.g. [`SyncListener`](https://github.com/bazelbuild/intellij/blob/6da9ea8dc591c0dabdd174073c7dd54b82889da6/base/src/com/google/idea/blaze/base/sync/SyncListener.java#L31-L33))
- [Action](https://plugins.jetbrains.com/docs/intellij/basic-action-system.html) (e.g. [`CopyBlazeTargetPathAction`](https://github.com/bazelbuild/intellij/blob/6d0a9dd314f67054486f222bc398786316d74240/base/src/com/google/idea/blaze/base/actions/CopyBlazeTargetPathAction.java#L33))
- [Project](https://plugins.jetbrains.com/docs/intellij/project.html)
- [Run Configuration](https://plugins.jetbrains.com/docs/intellij/run-configurations.html#run_configurations.md)
- [Run Profile State](https://plugins.jetbrains.com/docs/intellij/execution.html#architecture-overview).
- `ConfigurationContext` and `TestContext`:
	- A in-memory representations of "the code a gutter play icon points to".
	- Get passed to `RunConfigurationProducer`s.
- `RunConfigurationProducer`:
	- An interface that defines how a plugin can create Run Configurations.
	- It's responsible for translating a `ConfigurationContext` (or a `TestContext`) into zero or more `RunConfiguration`s.
	- The `base` Bazel plugin registers [a handful of `RunConfigurationProducer`s](https://github.com/bazelbuild/intellij/blob/6da9ea8dc591c0dabdd174073c7dd54b82889da6/base/src/META-INF/blaze-base.xml#L344-L355).
		- These producers are themselves extensible via Extension Points.
		- For instance, the `golang` sub-plugin can extend `BlazeBuildFileRunConfigurationProducer` to make sure IntelliJ knows how to run `go_binary` targets from BUILD files.
	- In short, the actions available from clicking a gutter icon are:
		- "the union of all `RunConfiguration`s produced by all registered `RunConfigurationProducer`s given a specific `TestContext`".

### Concepts Specific To The Bazel Plugin
These names keep popping up across the codebase, it pays to build a working knowledge of what they are as you navigate the sea of Java.
- `ijwb`: Stands for "IntelliJ With Bazel". Same for `clwb` (CLion With Bazel), and `aswb` (Android Studio With Bazel).

#### Sync
- `SyncPlugin`: 
	- Main entry point for a plugin.
	- The interface is defined in [`BlazeSyncPlugin`](https://github.com/bazelbuild/intellij/blob/09a2124a50669d7db3bfa7740436256f2a66023f/base/src/com/google/idea/blaze/base/sync/BlazeSyncPlugin.java#L44), individual methods are documented reasonably well.
	- Defines how a plugin will interact with the sync process via hooks.
	- For instance, a plugin can update the IntelliJ project structure (the thing you access with `Cmd+;`) by overriding `BlazeSyncPlugin.updateProjectStructure()`.

#### Targets
- `TargetMap`:
	- The in-memory representation of the Bazel build.
	- Used by per-language plugins for searching for things like:
		- Searching for references for a symbol.
		- Figuring out which Bazel target to run when creating a Run Configuration from a source file.
	- It gets created and refreshed after every sync.
	- It's globally accessible via the `BlazeProjectDataManager` (e.g. [here](https://github.com/bazelbuild/intellij/blob/cb64475c86980fc590c839fb0bb6f47b0bdc7b72/base/src/com/google/idea/blaze/base/dependencies/DependencyFinder.java#L55)).
- `TargetKey`: Keys to a `TargetMap`. Each entry reperesents a Bazel target.
- `TargetIdeInfo`: Values of a `TargetMap`. Each entry represents all the information the plugin gathered from a Bazel target (e.g. name, source files, dependencies...).
- `SourceToTargetMap`:
    - Interface for classes that map source files to targets.
    - Has functions to go from `File` to a `Label` and from `File` to a `TargetKey`.
    - Usually, implementors will hold a reference `TargetMap` that they look into to fulfil requests.
- `Kind`:
    - How a plugin represents a Bazel rule. [`py_binary`](https://github.com/bazelbuild/intellij/blob/0fb951c53e3ba3bdd08280054840359fe448ef45/python/src/com/google/idea/blaze/python/PythonBlazeRules.java#L30) is a `Kind`, and so is [`go_library`](https://github.com/bazelbuild/intellij/blob/ca9c95e73deb35d193938e00dc2a4c885bfe998a/golang/src/com/google/idea/blaze/golang/GoBlazeRules.java#L37).
    - `Kind`s are how the plugin knows how to parse BUILD files.
    - Sub-plugins can register their own `Kind`s of targets by implementing `Kind.Provider`.
    - If a rule or macro doesn't have a registered `Kind`, it will be ignored by the plugin when placing gutter icons on BUILD files.
        - For instance, if we wanted to enable running [`java_junit5_test` from bazel-contrib/rules_jvm](https://github.com/bazel-contrib/rules_jvm?tab=readme-ov-file#java_junit5_test) from a BUILD file, we'd have to add `java_junit5_test` as a `RuleType.TEST` to the relevant sub-plugin.

#### Configuration
- `ProjectView`:
	- The in-memory representation of a Project View File (a `.bazelproject` file).
	- You might need to parse/access project views if you:
		- Need to operate excluded/included directories (e.g. to check whether a particular file is excluded).
		- Need to check which languages are enabled.
    - A `ProjectView` is composed of zero or more `Section`s (e.g. `targets:` is a `Section`).
- `ProjectViewSet`:
    - `ProjectView`s are composable. For instance, you may `import` another `.bazelproject` file from your current `.bazelproject` file.
    - If a `ProjectView` is a single `.bazelproject` file, a `ProjectViewSet` represents the whole hierarchy of `ProjectView`s that affect your project.
    - Often, you'll be dealing with `ProjectViewSet`s instead of lone `ProjectView`s, even if you're dealing with only one file.
- `UserSetting`s:
    - A `UserSetting` is the informal name the Bazel plugin gives to the global settings you set for all projects using the plugin (via `Cmd+, -> Bazel`).
    - Most `UserSetting`s can be overriden in a `ProjectView`.

## Plugin Architecture
The Bazel IntelliJ Plugin is structured as a base plugin (that lives in `/base`), and a series of sub-plugins. There are [Other Directories](#other-directories), but we'll ignore those for now.

The base plugin contains:
- The core machinery for the plugin to work, such as the machinery for [Importing A Project](#importing-a-project), and [Syncing A Project](#syncing-a-project), and [Running Something](#running-something).
- A series of interfaces and Extension Points that per-language sub-plugins can use to extend the core functionality, such as "Debugging a Go target", or "Getting source information out of a `java_library`".

There is one sub-plugin per language supported by the plugin. Each sub-plugin lives in its own top level directory (e.g. `/java`, `/golang`...).

Per-language plugins are laid out like this:
```shell-session
$ tree -L 2 java
java
├── BUILD // There is only one BUILD file for each sub-plugin
├── src
│   ├── META-INF // Here live the XML files that configure the plugin 
|   |            // and hook it up to the relevant SDK APIs
│   └── com // The actual source code of the plugin
└── tests
    ├── integrationtests // Tests that need a headless IntelliJ instance to run 
    |                    // (e.g. tests that "I can go to definition for this symbol when the carat is at this row and column").
    |                    // Usually expensive to run.
    └── unittests // Tests that assert things about individual classes.
                  // Usually cheap to run.
```

When exploring a sub-plugin, the first point of call should always be `/<name>/src/META-INF/<config-file>.xml`. For instance, let's look at the config file for the Gazelle sub-plugin:

```xml
<idea-plugin>  
  <extensions defaultExtensionNs="com.google.idea.blaze">  
    <SyncListener implementation="com.google.idea.blaze.gazelle.GazelleSyncListener" />  
    <SyncPlugin implementation="com.google.idea.blaze.gazelle.GazelleSyncPlugin" />  
    <SettingsUiContributor implementation="com.google.idea.blaze.gazelle.GazelleUserSettingsConfigurable$UiContributor" />  
    <ProjectViewDefaultValueProvider implementation="com.google.idea.blaze.gazelle.GazelleSection$GazelleProjectViewDefaultValueProvider" />  
  </extensions>  
  <extensions defaultExtensionNs="com.intellij">  
    <applicationService serviceInterface="com.google.idea.blaze.gazelle.GazelleRunner"  
                        serviceImplementation="com.google.idea.blaze.gazelle.GazelleRunnerImpl"/>  
    <applicationService id="GazelleUserSettings" serviceImplementation="com.google.idea.blaze.gazelle.GazelleUserSettings"/>  
  </extensions>  
</idea-plugin>
```

Just by looking at this, we can guess a few things:
- We hook up to both the core of IntelliJ (via `<extensions defaultExtensionNs="com.intellij">`) _and_ the base Bazel plugin (via `<extensions defaultExtensionNs="com.google.idea.blaze">`).
- The Gazelle plugin wants to:
	- Interact with the sync process in some way (because we extend `SyncListener` and `SyncPlugin`).
	- Contribute some settings to the Bazel Plugin settings UI (`SettingsUiContributor`). This is the menu that pops up when you open "Preferences" in your IDE.
	- Provide a default value to the Project View (via `ProjectViewDefaultValueProvider`).
	- Registers an application service, `GazelleRunner`, which is a static (per-application) singleton that can run Gazelle commands.

It's a simple exercise, but this guessing game helps us decide where to start reading the code. This is important, because there's _so much_ code to read.

## Features
Let's walk through some common operations in the plugin. I'll try to list what the major classes are and how they interact together.

### Syncing A Project
In the context of the Bazel plugin, syncing is the act of translating the Bazel build into the IntelliJ project model. In general, the goal is to translate first-party source code into [Modules](https://plugins.jetbrains.com/docs/intellij/module.html), and third-party code into [Libraries](https://plugins.jetbrains.com/docs/intellij/library.html).

#### Sync TL;DR
The process is roughly:
- Save all files.
- **Build**: The plugin will build the desired directories with `bazel build` (if doing a full sync, build everything).
	- During the build, use the aspect in `/aspect` to traverse each target and output little text proto files, `*.intellij-info.txt`, that have all the information needed for the IDE, such as the location of generated files and downloaded third-party dependencies.
	- The aspect has special handling for every language. Some languages are simple ([like python](https://github.com/bazelbuild/intellij/blob/697b9a9a9fe4d0b3c0672a694d8bbd3a5e5a5d4e/aspect/intellij_info_impl.bzl#L335)), while some require a ton of Starlark (such as [the code to capture C++ toolchains](https://github.com/bazelbuild/intellij/blob/697b9a9a9fe4d0b3c0672a694d8bbd3a5e5a5d4e/aspect/intellij_info_impl.bzl#L489)).
- **Update**: The plugin will read those protos and:
	- Generates a `TargetMap`, an in-memory representation of the Bazel build, to be used later when we need to map sources to target (e.g. to figure out "which target do I need to run if I want to test this class").
	- Translates them into IntelliJ modules, updating the project structure as needed.
- After that, IntelliJ will realize that the project has changed, and will re-index the relevant symbols.

Here are the major classes that play a role in all syncs, in order of appearance:
- `BlazeSyncManager`: The entry point for a sync. When we request a sync, it always ends up going through `BlazeSyncManager.requestProjectSync`.
- `BlazeSyncParams`: The plugin supports many different kinds of syncs. Do we need to build the entire project, or just a directory? Or maybe we don't need to build anything at all. `BlazeSyncParams` encapsulates those options.
- `BlazeSyncStatus`: Project Service in charge of keeping the status of the sync. Other, asynchronous systems such as UI widgets can call on it to display information about the sync.
- `SyncPhaseCoordinator`: This is where the sync really starts to happen. Syncing has several steps (outlined in [[#Sync TL;DR]]), which this class orchestrates.
	- The most important method on this class is `SyncPhaseCoordinator.runSync()`. Reading this method will give you a great overview of the different phases and how they interact.
	- `SyncPhaseCoordinator` also offers extension points for different `SyncListeners`, which we'll see later.
- `SyncListener`: A `SyncListener` is a class that can react to sync events. There are three main hook-points that a `SyncListener` can latch on to:
	- `onSyncStart`: After the files have been saved, but before the Bazel build has started.
	- `onSyncComplete`: After we have have created the `TargetMap` and updated the IntelliJ modules, but _before_ we know whether the sync has been successful.
	- `afterSync`: After the sync has successfully completed.
	- For instance, the Gazelle sub-plugin overrides `onSyncStart` ([source](https://github.com/bazelbuild/intellij/blob/b7de8af67a457680088aefc8d60287631c9bb97b/gazelle/src/com/google/idea/blaze/gazelle/GazelleSyncListener.java#L191)) in order to run Gazelle before the build has started.

### Printing Build and Test Errors
The plugin runs many Bazel builds, both during a sync and when the user runs a `RunConfiguration`.

Whenever we run a Bazel command, the plugin runs every line of output through a predefined set of filters, called `IssueParser`s. For now, that list is centralized [here](https://github.com/bazelbuild/intellij/blob/6da9ea8dc591c0dabdd174073c7dd54b82889da6/base/src/com/google/idea/blaze/base/issueparser/BlazeIssueParser.java#L50).

These issues are forwarded to different views, such as the Bazel problems view, or a custom notification.

### Running Something
> Please read [Execution in the IntelliJ SDK](https://plugins.jetbrains.com/docs/intellij/execution.html#architecture-overview) before reading this section.

In general, the classes that have to do with running and debugging things are in the `run` subdirectory of each sub-plugin. So, `/golang/src/com/g...e/golang/run` will contain classes that enable running, testing, and debugging Go targets.

#### Gutter Icons: From `*Context` to `RunConfiguration`
Let's recap what we said in [Important Concepts You Should Know Before Continuing](#important-concepts-you-should-know-before-continuing):
- The plugin registers one or more `RunConfigurationProducer`s.
- When parsing a file, IntelliJ creates a `ConfigurationContext` and passes it to all registered `RunConfigurationProducer`s.
	- NB: This happens when we _parse_ the file, not when we click on the gutter icon. If you're debugging a gutter icon and want to trigger `RunConfiguration` generation, close the file and open it again.
- These producers register zero or more `RunConfiguration`s based on that context.
- These `RunConfiguration`s are the ones offered to the user when they click the gutter icon.

In the Bazel plugin, we extend one of four `RunConfigurationProducer`s:
- `BlazeBuildFileRunConfigurationProducer`: Generate `RunConfiguration`s from gutter icons in BUILD files.
- `BlazeFilterExistingRunConfigurationProducer`: Identify when we're trying to run a target/test we've run before, and use that instead of creating a new run configuration.
- `TestContextRunConfigurationProducer`: Generate `RunConfiguration`s from gutter icons in test sources (e.g. `lib_test.go`, or a JUnit test class).
- `BinaryContextRunConfigurationProducer`: Generate `RunConfiguration`s from gutter icons near binary entry points (e.g. the `int main() {` in a C program).

#### From `RunConfiguration` To Actually Running Things

Now, we hopefully have a nice list of "Run" and "Debug" run configurations.

How do they actually run?

Well... it's complicated. How to actually run a target is unique to every sub-plugin and testing framework. For instance, the way you connect to a Java debugger is not the same to the way you pass test filters to a Go test. There is a complex and horribly leaky web of abstractions that tries to model "how we run things".

However, there are some general principles that usually hold true:

- `RunConfiguration`s are stateful. They hold (and persist to disk) information about which target they are running, with which flags, and lots more data. This is encapsulated by the `RunConfigurationState` class. However, they don't hold the majority of their own state. Instead, they rely on...
- `RunConfiguration`s usually have `Handler`s. These are classes that implement the `BlazeCommandRunConfigurationHandler` interface, and have two jobs:
	- Hold, persist, and update the `RunConfigurationState` for a given run configuration.
	- Create `Runner`s, objects that can actually run the commands required by the run configuration. Speaking of Runners...
- `Runner`s are created by `Handler`s. These are classes that implement `BlazeCommandRunConfigurationRunner`.
	- A `Runner`'s main job is to create `RunProfileState`s. Essentially, they are responsible for crafting the command line that will run our target.
	- They also know several miscellaneous things such as "am I used for debugging or running?", which are useful for UI purposes.
	- If something needs to connect to a debugger, it's probably implemented in the `Runner`.

#### A Note About UI

You may have noticed that Bazel run configurations have a different UI than, say JUnit configurations, which themselves have different fields than Go test configurations.

The UI is, perhaps surprisingly, stored inside the `RunConfigurationState`. For instance, open [`BlazeCidrRunConfigState`](https://github.com/bazelbuild/intellij/blob/6da9ea8dc591c0dabdd174073c7dd54b82889da6/clwb/src/com/google/idea/blaze/clwb/run/BlazeCidrRunConfigState.java#L26) and see if you can spot where we set the working directory.


## Other Top-level Directories
Besides `/base` and the per-language plugins, there are several top-level directories that do interesting things in the plugin:

- `ijwb`, `clwb`, `aswb`: Contain targets to build the actual zip files that we end up releasing. They will depend on the different languages based on what the platform supports. For instance, `clwb` will depend on `/cpp`, but not on `/java`.
	- For instance, to build the IJ UE plugin for IntelliJ 2024.1, we could run:
		- `bazel build --define=ij_product=intellij-ue-2024.1 //ijwb:ijwb_bazel_zip`
- `aspect`: Starlark code that will traverse the build and output target information during sync, so that the plugin can parse it later.
- `build_defs`: The plugin uses a complex system of `--define` values to decide which JetBrains product and version you want to build for. When you pass `--define=ij_product=intellij-ue-latest`, this is where `ij_product` and `*-latest` are defined.
- `examples`: Self-contained example repositories to manually test the plugin. Every directory should have at least one `.bazelproject` file, and you should use that to import it.
- `intellij_platform_sdk`: BUILD files for building, re-packaging and depending on the IntelliJ Platform SDK.
- `plugin_dev`: Utility classes to create run configurations to debug the plugin itself.
- `querysync`: An alternative to regular sync, being worked on by Google. It's extremely experimental, and currently disabled. Safe to ignore.
- `sdkcompat`: A layer of compatibility between different IntelliJ SDK API versions. This code makes the rest of the code compatible with both IntelliJ 2022.1 and 2024.1.
- `testing`: Some utilities for running the plugin's tests.
- `third_party`: What it says on the tin.
- `tools`: Exactly what it means in other Bazel repositories.
