# Query Sync in the Bazel Plugin for IntelliJ

We are excited to introduce **Query Sync**, a new mode in the Bazel Plugin for IntelliJ, designed to significantly enhance your development experience. 
Currently it is enabled for Java, Kotlin and C++

Bazel users working on large projects often need to limit their scope because even high-performance devices can't handle the full project view. Previously, the solution was to create fine-grained project view files that imported only necessary components, expanding them dynamically when browsing code.

The new approach allows you to import a much larger scope from the beginning, enabling more advanced IDE features as you navigate the code. This doesn't have to be on a per-file basis—it can be per-directory or any other granularity. It's smart: if you enable analysis for one file, it's automatically enabled for all others that share the same dependencies. This way, you gain advanced features in your focus areas while still seeing the bigger picture and benefiting from navigation and code completions across a broader scope.

## Enabling Query Sync

1. Open **IntelliJ IDEA**.
2. Go to Settings -> Other Settings -> Query Sync
3. Check "Enable Query Sync for new projects"
4. Clean your project's old import files (`.ijwb` or `.aswb` directory
5. Import your project in the same way you were doing before

## Demos

### Setup, Import & Enable Analysis
https://github.com/user-attachments/assets/8547dcf5-7005-4db9-b555-e39af82ccd92

### Auto Sync
https://github.com/user-attachments/assets/28e42b6a-916e-495e-953d-e0e96644790a


