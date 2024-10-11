# Query Sync in the Bazel Plugin for IntelliJ

We are excited to introduce **Query Sync**, a new mode in the Bazel Plugin for IntelliJ, designed to significantly enhance your development experience. 
Currently it is enabled for Java, Kotlin and C++

## Key Features

1. **Instant Project Import**

   - Projects are imported almost immediately — no builds are required at the start.

2. **Per-File Analysis**

   - Each file displays an **"Enable Analysis"** button.
   - Builds and loads dependencies for that specific target when clicked.

3. **Reversed Dependency Analysis**

   - Option to **"Enable Analysis for Reversed Deps"**.
   - Enhances features like **"Find Usages"** to function smoothly across your codebase.

4. **Transitive Dependency Loading**

   - Resolves the issue of "shallow dependencies."
   - When analysis is enabled, all transitive dependencies are loaded.

5. **Direct Workspace Navigation**

   - Eliminates navigation to `srcjars` when debugging or using **"Go to Definition"**.
   - Always directs you to workspace files, even for targets not present in the project view file.

## How to Enable Query Sync

Before enabling Query Sync, please ensure you're using the Bazel Plugin from the **beta channel**.

### Steps to Switch to the Beta Channel

1. Open **IntelliJ IDEA**.
2. Navigate to **Settings** > **Plugins**.
3. Search for the **Bazel Plugin**.
4. Click on the plugin and select **Manage Plugin Repositories**.
5. Add the [beta](https://github.com/bazelbuild/intellij?tab=readme-ov-file#beta-versions) channel repository if not already present.
6. Update the plugin to the latest beta version.
7. Restart IntelliJ IDEA to apply changes.

### Enabling Query Sync via the Project View Wizard

1. Open **IntelliJ IDEA**.
2. Go to **File** > **Import Project...** or **File** > **New** > **Project from Existing Sources...** to start the **Project Import Wizard**.
3. Select your project's root directory and click **OK**.
4. In the **Import Project from Bazel** dialog, proceed with the import process.
5. When you reach the **Project View** setup step, enable **Query Sync** by setting the attrbute `use_query_sync` to `true`.
6. Complete the wizard to generate.
7. Your project is now configured to use Query Sync.

### Demo
https://github.com/user-attachments/assets/b7600841-2c1c-4ecd-acb6-e1dde044e7b1

