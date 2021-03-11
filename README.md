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

* `bazel build //ijwb:ijwb_bazel_zip --define=ij_product=intellij-latest`
* `bazel build //clwb:clwb_bazel_zip --define=ij_product=clion-latest`
* `bazel build //aswb:aswb_bazel_zip --define=ij_product=android-studio-latest`

from the project root. This will create a plugin zip file at
`bazel-bin/<PRODUCT>/<PRODUCT>_bazel.zip`, which can be installed directly
from the IDE. `<PRODUCT>` can be one of `ijwb, clwb, aswb`.

If the IDE refuses to load the plugin because of version issues, specify the
correct `ij_product`. These are in the form `<IDE>-<VERSION>` with `<IDE>`
being one of `intellij, clion, android-studio`, and `<VERSION>` being one
of `latest, beta, canary`.

If you are  using the most recent version of your IDE, you likely want
`--define=ij_product=<IDE>-beta` which will be the next version after
`<IDE>-latest`. Additionally, `canary` can be a largely untested `alpha` 
build of an upcoming version. A complete mapping of all currently defined 
versions can be found in  `intellij_platform_sdk/build_defs.bzl`.

You can import the project into IntelliJ (with the Bazel plugin)
via importing the `ijwb/ijwb.bazelproject` file.

## Contributions

We may be able to accept contributions in some circumstances. Some caveats:

  * Before opening a pull request, first file an issue and discuss potential
    changes with the devs. This will often save you time you would otherwise
    have invested in a patch which can't be applied.
  * We can't accept sylistic, refactoring, or "cleanup" changes.
  * We have very limited bandwidth, and applying patches upstream is a
    time-consuming process. Large patches generally can't be accepted unless
    there's clear value for all our users.
