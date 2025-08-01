# An IntelliJ plugin for [Bazel](http://bazel.build) projects

As of July 1, 2025, the Bazel for IntelliJ and Bazel for CLion plugins are maintained by JetBrains. These plugins are not offered by nor affiliated with Google. The Bazel trademark and logo are the property of Google LLC.

This project uses binary bundles licensed under [JetBrains User Agreement](https://www.jetbrains.com/legal/docs/toolbox/user/).

The Bazel plugin is uploaded to the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/8609-bazel) 
regularly from the state of this repository. See the [releases](https://github.com/bazelbuild/intellij/releases) 
tab for more information.

## Community
The Bazel project is hosting a Special Interest Group (SIG) for Bazel IntelliJ IDE plug-in. Details about the SIG and 
how to join the discussion can be found in the [SIG charter](https://github.com/bazelbuild/community/blob/main/sigs/bazel-intellij/CHARTER.md).

## Support

See the [documentation entry](https://github.com/bazelbuild/intellij/blob/master/docs/index.md)
on the plugin support across JetBrains products, languages, and operating
systems.

## Source Code Locations

The Bazel plugins for IntelliJ and CLion are built and released from the [master branch](https://github.com/bazelbuild/intellij) of this repository. An 
external team of maintainers addresses IntelliJ and CLion plugin issues and pull requests. 

The Bazel plugin for Android Studio is built and released from [AOSP](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/aswb/). The [google branch](https://github.com/bazelbuild/intellij/tree/google) is now deprecated.
The last snapshot of the Android Studio plugin that was hosted in this repository can be found on the 
[aswb branch](https://github.com/bazelbuild/intellij/tree/aswb).

Although the code in this repository and in AOSP share the same structure and core components, they have diverged from 
each other. 

- Changes for IntelliJ and CLion plugins are *only* merged into the master branch of this repository. Users can request 
  that certain fixes which are also needed for the Android Studio plugin get merged into the AOSP project. This will 
  require the internal teams’ approval. 
- Changes made by the internal Google teams are automatically exported to AOSP. These commits were regularly 
  cherry-picked to the master branch. See the next paragraph for details.
- The Android Studio plugin was removed from the master branch and is now only available in the AOSP. 

### Cherry-picks from AOSP to this project

After the migration of the Android Studio plugin to [AOSP](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/aswb/), this process was interrupted for approximately five months.

In November 2024, many changes from AOSP were cherry-picked in [6965](https://github.com/bazelbuild/intellij/pull/6965).
Starting from that point, we have been making our best effort to apply AOSP changes to the [master branch](https://github.com/bazelbuild/intellij/tree/master).

For this purpose, we use the Python tool [LeFrosch/intellij-aosp-merge](https://github.com/LeFrosch/intellij-aosp-merge), which is capable of adjusting paths from AOSP to this project's layout and allows us to compare revisions in AOSP and here.

Status of picked and skipped commits could be found in the following [googledoc](https://jb.gg/aosp-picks).

Due to multiple regressions caused by picks from the AOSP, cherry-picks have been halted for the time being.

## Installation

You can find our plugin in the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/8609-bazel)
or directly from the IDE by going to `Settings -> Plugins -> Marketplace`, and searching for `Bazel`.

### Beta versions

Beta versions are usually uploaded to the Beta channel 2 weeks before they become full releases. Ways to install them: 
- download and install them manually from the [Beta channel page](https://plugins.jetbrains.com/plugin/8609-bazel/versions/beta) on JetBrains Marketplace
- add the Beta channel to the IDE under `Settings -> Plugins -> Gear Icon -> Manage Plugin repositories` and add one of the following URLs depending on your product. 
  You can now find the latest Beta under `Settings -> Plugins -> Marketplace` or update the Bazel plugin to Beta if you already installed it.
  - IntelliJ IDEA -> `https://plugins.jetbrains.com/plugins/beta/8609`
  - CLion -> `https://plugins.jetbrains.com/plugins/beta/9554`

## Usage
We recommend watching [this video](https://www.youtube.com/watch?v=GV_KwWK3Qy8) to familiarize yourself with the plugin's features.

To import an existing Bazel project, choose `Import Bazel Project`,
and follow the instructions in the project import wizard.

Detailed docs are available [here](https://github.com/bazelbuild/intellij/blob/master/docs/index.md).

## Known issues

### Python debugging
Please read this comment https://github.com/bazelbuild/intellij/issues/4745#issue-1668398619

### Mixed Python & Java projects
In order to get correct python highlighting, please try to open "Project Structure" window and set "Python facet" there

### Remote Development
To properly set up Remote Development (https://www.jetbrains.com/remote-development/), follow these steps:
1. Create an empty project on the remote machine (this can be just an empty directory).
2. Import the project using Remote Development.
3. Install the Bazel Plugin on the host machine.
4. Close the project.
5. Open the initially intended project.

## Building the plugin

Install Bazel, then build the target `*:*_bazel_zip` for your desired product:

* `bazel build //ijwb:ijwb_bazel_zip --define=ij_product=intellij-ue-oss-latest-stable`
* `bazel build //clwb:clwb_bazel_zip --define=ij_product=clion-oss-latest-stable`

from the project root. This will create a plugin zip file at
`bazel-bin/<PRODUCT>/<PRODUCT>_bazel.zip`, which can be installed directly
from the IDE. `<PRODUCT>` can be one of `ijwb, clwb, aswb`.

If the IDE refuses to load the plugin because of version issues, specify the
correct `ij_product`. These are in the form `<IDE>-oss-<VERSION>` with 
  * `<IDE>` being one of `intellij-ue, intellij, clion`, 
  * `<VERSION>` being one of `oldest-stable, latest-stable, under-dev`.
Alternatevely, for you can set `ij_product` to direct IntelliJ or CLion versions, for example `clion-2023.2`, `intellij-2023.2` or `intellij-ue-2023.2`

Note that there is a difference between `intellij` and `intellij-ue`.
`ue` stands for IntelliJ Ultimate Edition and contains additional 
features for JavaScript as well as Go.

`<IDE>-oss-oldest-stable` and `<IDE>-oss-latest-stable` are aliases for the two IDE versions
that the plugin is officially compatible with at a given time. `<IDE>-oss-latest-stable` usually 
maps to the last released IDE version while `<IDE>-oss-oldest-stable` maps to the one right before that, 
e.g. `<IDE>-oss-oldest-stable=2022.1` and `<IDE>-oss-latest-stable=2022.2`. Additionally, 
`<IDE>-oss-under-dev` represents the upcoming version of the IDE that we are working towards 
supporting. A complete mapping of all currently defined versions can be found in 
`intellij_platform_sdk/build_defs.bzl`.

You can import the project into IntelliJ (with the Bazel plugin)
via importing the `ijwb/ijwb.bazelproject` file.

## Compatibility with IDE Versions

You can build the plugin for different IDE versions by adjusting the `ij_product` 
option either from command line or by updating the `.bazelproject` file to specify
the desired value for `ij_product` under `build_flags`. 

We have three aliases for product versions;
  * `oldest-stable` is the oldest IDE version supported by the Bazel plugin released to 
  the JetBrains stable channel.
  * `latest-stable` is the latest IDE version supported by the Bazel plugin released to
  the JetBrains stable channel.
  * `under-dev` is the IDE version we are currently working towards supporting.

The current corresponding IDE versions of these aliases can be found [here](./intellij_platform_sdk/build_defs.bzl#L31).

## Contributions

We welcome contributions to support new IDE versions. However, to make
the review process faster and easier, we recommend the following:

  * We can only accept small pull requests. Smaller pull requests tend to have 
    fewer review comments and hence can get submitted much faster. They also tend
    to conflict less with our internal code base, simplifying the integration for us. 
    For example, you should have separate pull requests each focusing on a certain incompatible change
    rather than having a large pull request fixing multiple ones.
    
  * Since we continue to support a number of IDE versions while working on a new
    one, you need to make sure that your proposed changes do not break
    older versions. Our presubmit pipeline will take care of testing your changes
    against all the supported versions and lets you know whether it broke anything.
    
  * To facilitate merging your changes into upstream, we recommend following
    our procedure for supporting SDK backward-compatibility. 
    
    * First consider adjusting the plugin code so that it directly works with different IDE versions. 
      Example strategies for this would be:
      
      * Switching to a (possibly newer) IntelliJ platform API which is available in all relevant IDE versions. Example: [pr/2623](https://github.com/bazelbuild/intellij/pull/2623)
      * Switching to a raw class by removing a generic type parameter which differs across versions. Example: [pr/2631](https://github.com/bazelbuild/intellij/pull/2631)
    
    * For non-trivial incompatible changes, the code for maintaining SDK compatibility lives
      in [sdkcompat](./sdkcompat) and [testing/testcompat](./testing/testcompat) directories, where `testing/testcompat`
      holds test-only SDK compatibility changes. Each of the two directories contains a sub-folder per supported IDE version with 
      version-specific implementations. The outside API of all classes must be the same across versions, just 
      the implementation may differ. When introducing a new file in this directory, make sure to duplicate
      it appropriately across all versions.  
      We follow these three techniques for non-trivial incompatible changes.
    
      * **Compat**  
         Preferred to Adapter and Wrapper when applicable. We add a util-class with 
         only static methods and a private constructor and wrap the changed method by one of the 
         static methods. If the change is small enough, you do not need to create a new util-class
         and should add the change to [BaseSdkCompat class](./sdkcompat/v222/com/google/idea/sdkcompat/general/BaseSdkCompat.java) 
         instead. Example: [pr/2345](https://github.com/bazelbuild/intellij/pull/2345)

      * **Adapter**  
         Used when we extend a super class and its constructor is updated.
         We create a new class extending the changed super class then extend this new class
         from the plugin code. Example: [pr/2352](https://github.com/bazelbuild/intellij/pull/2352)

      * **Wrapper**  
        Created when a new interface is used in a super class constructor. We create
        a wrapper class that wraps and supplies the old or the new interface based on
        the SDK version and use this wrapper class in the plugin code.
        Example: [pr/2166](https://github.com/bazelbuild/intellij/pull/2166)

  * All compat changes must be commented with `#api{API_VERSION}`, e.g. `#api203`.
    This represents the last API version that requires the code, i.e. the one before
    the version you aim to support. This is needed to make it easier to find and
    clean up this functionality when paving old versions.
    
  * Compat classes must never import plugin code and we try to keep the logic and code in them 
    as minimal as possible.

  
We may also be able to accept contributions to fix general issues or adding new features with some caveats:

  * Before opening a pull request, first file an issue and discuss potential
    changes with the devs. This will often save you time you would otherwise
    have invested in a patch which can't be applied.
  * Improvements for old not supported IDE versions will not be accepted.
    Your changes should target the currently supported IDE versions. 
    You can find a list of these versions [here](./intellij_platform_sdk/build_defs.bzl#L31).
  * We can't accept stylistic, refactoring, or "cleanup" changes.
  * We have very limited bandwidth, and applying patches upstream is a
    time-consuming process. Large patches generally can't be accepted unless
    there's clear value for all our users.
    
