# IntelliJ Development Instance Setup

## Determine which JDK you're running, and where it is

Inside IntelliJ, go to the SDK menu (`Cmd+;` or `Ctrl+;`).

Go to `Project`, and look under `SDK` to find the JDK you intend to use
to develop the plugin.

Press `Edit`, and make a note of `JDK home path`, as well as the version of the JDK.

## Download and Install the JetBrains Runtime SDK

Most of the time, the IntelliJ Platform Plugin SDK bundled with your IntelliJ installation shuould be enuogh to compile and run the plugin.

To install it, please head to the SDK menu (`Cmd+;` or `Ctrl+;`), and then to `Platform Settings -> SDKs`.

In the list of SDKs, press the `+` icon, then `Add IntelliJ Platform Plugin SDK`. A file explorer window should open in the `Contents` directory of your IntelliJ installation. Select that directory.

Make sure that the `Internal Java Platform` is set to the JDK you want to use to develop your plugin.

## Import the plugin project.

Clone and import this repository as you would any other Bazel project.
When asked, use the bundled project view file (`ijwb/ijwb.bazelproject`). This will:

- Set the plugin to build with the latest development version by default.
- Automatically import the relevant target, and exclude the irrelevant ones.

Depending on which JetBrains product you're targetting, you may want to adjust the `--define` flag in the `build_flags` section. For more information on which values you can pass, please refer to [Building the Plugin](README.md#building-the-plugin)

## Create a run configuration and add appropriate flags

Once the project is imported, create a new Run configuration: `Run -> Edit Configurations...`.
Click the `+` sign, and create a new `Bazel IntelliJ Plugin` run configuration.
Configure the `Plugin SDK` field to use the SDK you've just installed.

### If Using JDK17

You'll need to add some additional flags to the VM Options to open the required modules.
You can find an up to date list in [the JetBrains/intellij-community repository](https://raw.githubusercontent.com/JetBrains/intellij-community/master/plugins/devkit/devkit-core/src/run/OpenedPackages.txt)
