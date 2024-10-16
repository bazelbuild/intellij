## Fast Build

The IntelliJ plugin for Bazel includes the **Fast Build** feature, allowing you to run tests and executables without recompiling the entire target. This feature detects which Java files have changed and compiles only those files, significantly speeding up the development process.

## Requirements

To enable Fast Build, you must add specific VM options to your IntelliJ configuration. Follow these steps:

1. Navigate to **Help** -> **Edit Custom VM Options**.
2. Add the following entries:

```plaintext
--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
```

## How to Use

To use Fast Build, locate your test or executable in the editor. Click the gutter icon and select either **Fast Run** or **Fast Test**.
