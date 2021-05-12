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

Th current corresponding IDE versions of these aliases are:

| Product-version alias            | Version |
| ---------------------------------|:-------:|
| intellij-ue-oss-stable           | 2020.3  |
| intellij-ue-oss-beta             | 2020.3  |
| intellij-ue-oss-under-dev        | 2021.1  |
| intellij-oss-stable              | 2020.3  |
| intellij-oss-beta                | 2020.3  |
| intellij-oss-under-dev           | 2021.1  |
| android-studio-oss-stable        | 2020.3  |
| android-studio-oss-beta          | 2020.3  |
| android-studio-oss-under-dev     | 2021.1  |
| clion-oss-stable                 | 2020.3  |
| clion-oss-beta                   | 2020.3  |
| clion-oss-under-dev              | 2021.1  |

## Contributions

We welcome contributions to support new IDE versions. However, to make
the review process faster and easier, we recommend the following:

  * Try to make changes in smaller patches. A smaller pull request will 
    make the review faster and more thorough. You are encouraged to have 
    separate pull requests each focusing on a ceratin incompatible change
    rather than having a large pull request fixing multiple ones.
    
  * Since we continue to support a number of IDE versions while working on a new
    one, you need to make sure that your proposed changes does not break
    older versions. Our presubmit pipeline will take care of testing your changes
    against all the supported versions and lets you know it broke anything.
    
  * To facilitate merging your changes into upstream, we recommend following
    our procedure for supporting SDK backward-compatibility if needed. The code
    for maintaining SDK compatibility lives in
    [sdkcompat](https://github.com/bazelbuild/intellij/tree/master/sdkcompat) directory.
    We follow these three techniques for non-trivial incompatible changes.
    
    * **Compat**  
       Preferred for small changes like a change in the return type of a method. 
       We add a util-class with only static methods and a private constructor and
       wrap the changed method by one of the static methods. If the change is small enough,
       you do not need to create a new util-class and should add a new method in 
       [BaseSdkCompat class](https://github.com/bazelbuild/intellij/blob/master/sdkcompat/v201/com/google/idea/sdkcompat/general/BaseSdkCompat.java) instead. Example: [pr/2345](https://github.com/bazelbuild/intellij/pull/2345)
       
    * **Adapter**  
       Used when we extend a super class and an overriden method signature changes
       (e.g. a new parameter is added) or the super class constructor is updated.
       We create a new class extending the changed super class to override the updated methods appropriately then extend this new class
       from the plugin code. Example: [pr/2114](https://github.com/bazelbuild/intellij/pull/2114)
       
    * **Wrapper**  
      Created when a new interface is used in a super class constructor. We create
      a wrapper class that wraps and supplies the old or the new interface based on
      the SDK version and use this wrapper class in the plugin code.
      Example: [pr/2166](https://github.com/bazelbuild/intellij/pull/2166)

  * All compat changes should be commented with #api{API_VERSION}, e.g. #api203.
    This represents the last API version that requires the code, i.e. the one before
    the version you aim to support. This is needed to make it easier to find and
    clean this functionality when paving old versions.
    
  * Compat classes must never import plugin code and we try to avoid control flow in them.

  
We aslo accept contributions to fix general issues or adding new features with some caveats:

  * Before opening a pull request, first file an issue and discuss potential
    changes with the devs. This will often save you time you would otherwise
    have invested in a patch which can't be applied.
  * Your changes should target one of the currently supported IDE versions. 
    You can find a list of these versions [here](https://github.com/bazelbuild/intellij/blob/master/intellij_platform_sdk/build_defs.bzl#L31).
    Improvements to older versions will not be accepted.
  * We can't accept sylistic, refactoring, or "cleanup" changes.
  * We have very limited bandwidth, and applying patches upstream is a
    time-consuming process. Large patches generally can't be accepted unless
    there's clear value for all our users.
    
