# An IntelliJ plugin for [Bazel](http://bazel.build) projects

This is an early-access version of our Bazel plugins for IntelliJ,
Android Studio, and CLion.

This repository is generally in a state matching the most recently uploaded 
plugins in the JetBrains' plugin repository. See the 
[releases](https://github.com/bazelbuild/intellij/releases) tab for more
information.

## Support

See the [support matrix](https://ij.bazel.build/docs/bazel-support.html)
on the various plugin support levels across JetBrains products, languages,
and operating systems.

## Installation

You can find our plugin in the Jetbrains plugin repository by going to
`Settings -> Browse Repositories`, and searching for `Bazel`.

## Usage

To import an existing Bazel project, choose `Import Bazel Project`,
and follow the instructions in the project import wizard.

Detailed docs are available [here](http://ij.bazel.build).


## Building the plugin

Install Bazel, then build the target `*:*_bazel_zip` for your desired product:

* `bazel build //ijwb:ijwb_bazel_zip --define=ij_product=intellij-ue-oss-stable`
* `bazel build //clwb:clwb_bazel_zip --define=ij_product=clion-oss-stable`
* `bazel build //aswb:aswb_bazel_zip --define=ij_product=android-studio-oss-stable`

from the project root. This will create a plugin zip file at
`bazel-bin/<PRODUCT>/<PRODUCT>_bazel.zip`, which can be installed directly
from the IDE. `<PRODUCT>` can be one of `ijwb, clwb, aswb`.

If the IDE refuses to load the plugin because of version issues, specify the
correct `ij_product`. These are in the form `<IDE>-oss-<VERSION>` with 
  * `<IDE>` being one of `intellij-ue, intellij, clion, android-studio`, 
  * `<VERSION>` being one of `stable, beta, under-dev`.

Note that there is a difference between `intellij` and `intellij-ue`.
`ue` stands for IntelliJ Ultimate Edition and contains additional 
features for JavaScript as well as Go.

If you are using the most recent version of your IDE, you likely want
`--define=ij_product=<IDE>-oss-beta` which will be the next version after
`<IDE>-oss-stable`. Additionally, `under-dev` can be a largely untested `alpha` 
build of an upcoming version. A complete mapping of all currently defined 
versions can be found in  `intellij_platform_sdk/build_defs.bzl`.

You can import the project into IntelliJ (with the Bazel plugin)
via importing the `ijwb/ijwb.bazelproject` file.

## Compatibility with IDE Versions

You can build the plugin for different IDE versions by adjusting the `ij_product` 
option either from command line or by updating the `.bazelproject` file to specify
the correct value for `ij_product` under `build_flags`. 

We have three aliases for product versions;
  * `stable` is the IDE version supported by the Bazel plugin released to 
  the JetBrains stable channel.
  * `beta` is the IDE version supported by the Bazel plugin released to
  the JetBrains Beta channel.
  * `under-dev` is the IDE version we are currently working towards supporting.

The current corresponding IDE versions of these aliases can be found [here](./intellij_platform_sdk/build_defs.bzl#L31).

## Contributions

We welcome contributions to support new IDE versions. However, to make
the review process faster and easier, we recommend the following:

  * We can only accept small pull requests. Smaller pull requests tend to have 
    less review comments and hence can get submitted much faster. They also tend
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
         and should add the change to [BaseSdkCompat class](./sdkcompat/v203/com/google/idea/sdkcompat/general/BaseSdkCompat.java) 
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
  * We can't accept sylistic, refactoring, or "cleanup" changes.
  * We have very limited bandwidth, and applying patches upstream is a
    time-consuming process. Large patches generally can't be accepted unless
    there's clear value for all our users.
    
