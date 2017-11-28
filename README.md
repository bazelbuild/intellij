# An IntelliJ plugin for [Bazel](http://bazel.build) projects

This is an early-access version of our Bazel plugins for IntelliJ,
Android Studio, and CLion.

This repository is currently updated semi-regularly. It's generally
in a state matching the most recently uploaded plugins in the
JetBrains' plugin repository.

## Installation

You can find our plugin in the JetBrains' plugin repository by going to
`Settings -> Browse Repositories`, and searching for `Bazel`.

## Usage

To import an existing Bazel project, choose `Import Bazel Project`,
and follow the instructions in the project import wizard.

Detailed docs are available [here](http://ij.bazel.build).

## Building the plugin

Install Bazel, then run

```bazel build //ijwb:ijwb_bazel_zip --define=ij_product=intellij-latest```

from the project root. This will create a zip file at
`bazel-bin/ijwb/ijwb_bazel.zip`, which can be installed directly
from the IDE.

## Contributions

We welcome contributions! Some caveats:

  * Please consider filing an issue prior to investing a lot of time
    in a patch.
  * In general, we prefer contributions that fix bugs or add features
    (as opposed to stylistic, refactoring, or "cleanup" changes).
