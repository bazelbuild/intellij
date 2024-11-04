# Query Sync in the Bazel Plugin for IntelliJ

We are excited to introduce **Query Sync**, a new mode in the Bazel Plugin for IntelliJ, designed to significantly enhance your development experience. 
Currently it is enabled for Java, Kotlin and C++

Bazel users working on large projects often need to limit their scope because even high-performance devices can't handle the full project view. Previously, the solution was to create fine-grained project view files that imported only necessary components, expanding them dynamically when browsing code.

The new approach allows you to import a much larger scope from the beginning, enabling more advanced IDE features as you navigate the code. This doesn't have to be on a per-file basis—it can be per-directory or any other granularity. It's smart: if you enable analysis for one file, it's automatically enabled for all others that share the same dependencies. This way, you gain advanced features in your focus areas while still seeing the bigger picture and benefiting from navigation and code completions across a broader scope.

## How to Enable Query Sync

### Enabling Query Sync via the Project View Wizard

1. Open **IntelliJ IDEA**.
2. Go to **File** > **Import Project...** or **File** > **New** > **Project from Existing Sources...** to start the **Project Import Wizard**.
3. Select your project's root directory and click **OK**.
4. In the **Import Project from Bazel** dialog, proceed with the import process.
5. When you reach the **Project View** setup step, enable **Query Sync** by setting the attribute `use_query_sync` to `true`.
   ```
   use_query_sync: true
   ```
6. Complete the wizard to generate.
7. Your project is now configured to use Query Sync.

### Demo
https://github.com/user-attachments/assets/b7600841-2c1c-4ecd-acb6-e1dde044e7b1

