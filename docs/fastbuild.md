## Fast Build

The IntelliJ plugin for Bazel includes the **Fast Build** feature, allowing you to run tests and executables without recompiling the entire target. This feature detects which Java files have changed and compiles only those files, significantly speeding up the development process.

## Requirements

To enable Fast Build, you must add specific VM options to your IntelliJ configuration.
The flags are required to run Java compilation inside the IntelliJ process, so that there's
no need to spawn a separate `javac` process.
Follow these steps:

1. Navigate to **Help** -> **Edit Custom VM Options**.
2. Add the following entries:

```plaintext
--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
```

## rules_java
Please use rules_java 8.1.0 or newer

## How to Use

To use Fast Build, locate your test or executable in the editor. Click the gutter icon and select either **Fast Run** or **Fast Test**.

## Demo

https://github.com/user-attachments/assets/73b4ffbb-043a-4b96-81f8-de5fb2a2a1eb

