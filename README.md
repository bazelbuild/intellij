# An IntelliJ plugin for [Bazel](http://bazel.io) projects

## Installation

You can find our plugin in the Jetbrains plugin repository by going to
IntelliJ -> Settings -> Browse Repositories, and searching for 'IntelliJ with Bazel'.

## Usage

To import an existing Bazel project, choose 'Import Bazel Project',
and follow the instructions in the project import wizard.

## Building the plugin

Install Bazel, then run 'bazel build //ijwb:ijwb_bazel' from
the project root. This will create a plugin jar in
'bazel-genfiles/ijwb/ijwb_bazel.jar'.