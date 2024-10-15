# Using Gazelle to Keep BUILD Files Updated

Gazelle can automatically generate and update your Bazel `BUILD` files based on the imports in your source code. To keep your `BUILD` files in sync without manual intervention, configure Gazelle to run before each sync operation.

## Setup Instructions

1. **Add `gazelle_target` to Your Project View File**: Include the `gazelle_target` attribute in your project view file to specify the Gazelle target to run.

   ```yaml
   # Example project view file
   directories:
     src/
   gazelle_target: //:gazelle
   ```

2. **Automatic Execution**: With the `gazelle_target` set, Gazelle will run automatically before every sync, updating your `BUILD` files according to your source code imports.

## Example Project

For a practical example, see this [Gazelle project setup](https://github.com/bazelbuild/intellij/tree/master/examples/go/with_proto).

## Demo

https://github.com/user-attachments/assets/b130bf56-49ec-46cc-810f-c831672e2601

