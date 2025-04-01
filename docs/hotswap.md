# JVM Hotswapping with the Bazel Plugin in IntelliJ IDEA

The Bazel Plugin for IntelliJ IDEA supports JVM hotswapping, allowing you to modify code during a debug session without restarting your application.

## Steps

1. **Start a Debugger Session**
   - Run your Bazel application in debug mode.

2. **Modify Your Code**
   - Make changes to your source files while the application is running.

3. **Compile and Reload**
   - Go to **Run** > **Debugging actions** > **Compile and Reload Modified Files**.

## Notes

- **Supported Changes**: Edits within method bodies.
- **Unsupported Changes**: Adding methods, fields, or altering class structures may require a restart.

## Demo
https://github.com/user-attachments/assets/a0f602d3-815b-4980-8194-3114971d2899

