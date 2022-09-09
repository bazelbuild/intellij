# IntelliJ Development Instance Setup

## Determine which JDK you're running, and where it is

Inside IntelliJ, go to the SDK menu (`Cmd+;` or `Ctrl+;`).

Go to `Project`, and look under `SDK` to find the JDK you intend to use
to develop the plugin.

Press `Edit`, and make a note of `JDK home path`, as well as the version of the JDK.

## Download and Install the JetBrains Runtime SDK

Head to [the JetBrains Runtime releases page](https://github.com/JetBrains/JetBrainsRuntime/releases)
and pick a version of the `JBRSDK` that matches your OS, architecture, and JDK version.

Install it appropriately:

- If you've downloaded a tarfile, just note the directory where you untarred it.
- If you're on macOS and have chosen a `.pkg` release, it will be installed under `/Library/Java/JavaVirtualMachines/jbrsdk-<version and os>/Contents`

Back in IntelliJ, head to the SDK menu (`Cmd+;` or `Ctrl+;`), and go to `SDKs` in the sidebar.

Add a new SDK by pressing the `+` button at the top.
Pick `Add IntelliJ Platform Plugin SDK`, and select the path where you've just installed it.

Add the java standard library, so that IntelliJ can index common symbols such as `java.io.File`:

- Head to the "Classpath" tab under the IntelliJ Platform SDK you've just created, and press the `+` icon.
- Navigate to the root directory of the SDK you've noted before (e.g. `/opt/homebrew/Cellar/openjdk@17/17.0.4.1/libexec/openjdk.jdk/Contents/Home` for an openjdk installed via Homebrew).
- Navigate to `<jdk_home>/Contents/Home/lib/jrt-fs.jar`, and open it.
- Apply the changes in IntelliJ.

## Import the plugin project.

Clone and import this repository as you would any other Bazel project.

## Create a run configuration and add appropriate flags

Once the project is imported, create a new Run configuration: `Run -> Edit Configurations...`.
Click the `+` sign, and create a new `Bazel IntelliJ Plugin` run configuration.
Configure the `Plugin SDK` field to use the SDK you've just installed.

### If Using JDK17

You'll need to add some additional flags to the VM Options:

```
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.desktop/java.awt.event=ALL-UNNAMED
--add-opens=java.desktop/sun.font=ALL-UNNAMED
--add-opens=java.desktop/java.awt=ALL-UNNAMED
--add-opens=java.desktop/sun.awt=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.desktop/javax.swing=ALL-UNNAMED
--add-opens=java.desktop/sun.swing=ALL-UNNAMED
--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED
--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED
--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED
--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED
--add-exports=java.desktop/sun.font=ALL-UNNAMED
--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED
--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED
--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED
```
