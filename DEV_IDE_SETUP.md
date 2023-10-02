# IntelliJ Development Instance Setup

## Import the plugin project.

Clone and import this repository as you would any other Bazel project.
When asked, use the bundled project view file (`ijwb/ijwb.bazelproject`). This will:

- Set the plugin to build with the latest development version by default.
- Automatically import the relevant target, and exclude the irrelevant ones.

Depending on which JetBrains product you're targetting, you may want to adjust the `--define` flag in the `build_flags` section. For more information on which values you can pass, please refer to [Building the Plugin](README.md#building-the-plugin)

## Download and Install the JetBrains Runtime SDK

Most of the time, the IntelliJ Platform Plugin SDK bundled with your IntelliJ installation shuould be enuogh to compile and run the plugin.

To install it, please head to the SDK menu (`Cmd+;` or `Ctrl+;`), and then to `Platform Settings -> SDKs`.

In the list of SDKs, press the `+` icon, then `Add IntelliJ Platform Plugin SDK`. 

<img width="1021" alt="image" src="https://github.com/bazelbuild/intellij/assets/326857/d5323a70-d93b-4734-a64c-d8018e19adc1">

A file explorer window should open in the `Contents` directory of your IntelliJ installation. Select that directory.

<img width="655" alt="image" src="https://github.com/bazelbuild/intellij/assets/326857/ce7b591f-ca51-42e5-8038-07f2a102a700">

Make sure that the `Internal Java Platform` is set to the JDK you want to use to develop your plugin. It is recommended to use JBR 17.

<img width="464" alt="image" src="https://github.com/rogerhu/intellij/assets/326857/28f05554-a4d8-43f4-b2bb-b971a7ee6da7">

## Set the project-wide SDK

Inside IntelliJ, go to the SDK menu (`Cmd+;` or `Ctrl+;`).

Go to `Project`, and look under `SDK` to find the JetBrains Runtime SDK installed in the previous step.

<img width="1019" alt="image" src="https://github.com/bazelbuild/intellij/assets/326857/356b1d93-b9a5-4366-9ed4-6b0a967d3fee">

## Create a run configuration and add appropriate flags

Once the project is imported, create a new Run configuration: `Run -> Edit Configurations...`.

Click the `+` sign, and create a new `Bazel IntelliJ Plugin` run configuration (the Bazel IntelliJ plugin needs to be installed):

<img width="1038" alt="image" src="https://github.com/bazelbuild/intellij/assets/326857/b1710978-437f-47ee-a490-ea0d1ab39985">

Configure the `Plugin SDK` field to use the SDK you've just installed.

<img width="1031" alt="image" src="https://github.com/bazelbuild/intellij/assets/326857/92e107d3-e9f6-479c-a127-4f4202b21f41">

