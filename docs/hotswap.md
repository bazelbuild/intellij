# JVM Hotswapping with the Bazel Plugin in IntelliJ IDEA

The Bazel Plugin for IntelliJ IDEA supports JVM hotswapping, allowing you to modify code during a debug session without restarting your application.

## Steps

1. **Start a Debugger Session**
   - Run your Bazel application in debug mode (only `bazel run` launches are supported).

2. **Modify Your Code**
   - Make changes to your source files while the application is running.

3. **Compile and Reload**
   - Go to **Build** > **Bazel: Apply HotSwap**.

## Notes

- **Supported Changes**: Edits within method bodies.
- **Unsupported Changes**: Adding methods, fields, or altering class structures may require a restart.
