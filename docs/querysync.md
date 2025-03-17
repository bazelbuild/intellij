# (experimental) Speeding up Project Import and Re-Sync with Query Sync

We are excited to introduce **Query Sync**, a new mode in the Bazel Plugin for IntelliJ, designed to significantly enhance your development experience. 
Currently it is enabled for Java, Kotlin and C++

Bazel users working on large projects often need to limit their scope because even high-performance devices can't handle the full project view. Previously, the solution was to create fine-grained project view files that imported only necessary components, expanding them dynamically when browsing code.

The new approach allows you to import a much larger scope from the beginning, enabling more advanced IDE features as you navigate the code. This doesn't have to be on a per-file basis — it can be per-directory or any other granularity. Furthermore, if you enable analysis for one file, it's automatically enabled for all others that share the same dependencies. This way, you gain advanced features in your focus areas while still seeing the bigger picture and benefiting from navigation and code completions across a broader scope.

## "Sync" and "Enable Analysis" Actions

In Query Sync mode, you'll interact with two primary actions in the Bazel Plugin for IntelliJ: **Sync** and **Enable Analysis**.

1. **Sync**: The Sync action remains crucial for synchronizing your project with the Bazel build system, and it's now faster than ever. After clicking Sync, you'll be able to navigate your codebase almost immediately. This quick sync allows you to browse files and perform basic code navigation. However, some advanced features — such as access to external dependencies - may not be fully available at this point.

2. **Enable Analysis**: To unlock the full suite of IDE capabilities, especially those involving external dependencies and code outside the `directories` specified in your Project View file, use the Enable Analysis action. This action initiates a deeper analysis of your codebase, providing comprehensive features like code completion for external deps and thorough error checking. Enabling analysis for one file automatically extends these benefits to other files that share the same dependencies, enhancing your development experience without manually configuring each file.

## Enabling Query Sync
There are two ways to enable the Query Sync mode:

### In the Project View file
1. No cleanup required
2. Open the Project View file
3. Add this line: `use_query_sync: true`
4. Sync the project

### In Settings

1. Open **IntelliJ IDEA**.
2. Go to Settings -> Other Settings -> Query Sync
3. Check "Enable Query Sync for new projects"
4. Clean your project's old import files (`.ijwb` or `.aswb` directory)
5. Import your project in the same way you were doing before

## Demos

### Setup, Import & Enable Analysis
https://github.com/user-attachments/assets/8547dcf5-7005-4db9-b555-e39af82ccd92

