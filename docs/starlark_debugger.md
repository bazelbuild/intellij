# Debugging Starlark with the IntelliJ Plugin

To debug Starlark, you need to create a `build` run configuration. You can do this in two ways: manually create a new configuration or reuse an existing one.

## Method 1: Create a Run Configuration Manually

1. Open the **Run Configurations** menu.
2. Click the `+` icon to add a new configuration.
3. Select **Bazel Command** from the list.
4. Enter the target expression in the **Target** field.
5. Set the **Bazel Command** field to `build`.

## Method 2: Reuse an Existing Run Configuration

1. Run the target using an existing configuration.
2. Open the **Run Configurations** editor.
3. Change the **Command** field to `build`.
4. Run the configuration again.

**Important:** Before starting the debugger, ensure you have modified something in the Starlark file. If no changes are made, Bazel will use the analysis cache and skip executing the Starlark code.

## Demo: Showing Both Methods
https://github.com/user-attachments/assets/8511bd9f-6d47-4627-b617-0c90edd9f30b

