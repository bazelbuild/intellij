# An IntelliJ plugin for [Bazel](http://bazel.build) projects

This is an early-access version of our Bazel plugins for IntelliJ,
Android Studio, and CLion.

This repository is currently updated semi-regularly. It's generally
in a state matching the most recently uploaded plugins in the
JetBrains' plugin repository.

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

If the IDE refuses to load the plugin because of version issues, specify
`ij_product` manually. A mapping of product `latest` to direct versions can be
found in `intellij_platform_sdk/build_defs.bzl`.

## Contributions

We welcome contributions! Some caveats:

  * Please consider filing an issue, and discussing potential changes
    with the devs, prior to investing a lot of time in a patch.
  * In general, we prefer contributions that fix bugs or add features
    (as opposed to stylistic, refactoring, or "cleanup" changes).
