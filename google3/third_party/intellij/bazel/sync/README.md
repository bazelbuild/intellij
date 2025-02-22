# IJ bazel core sync libraries

This folder contains the IJ core sync libraries. These libraries contain the
core logic for bazel sync in IJ, and are intended to be portable. This means:

- They cannot depend on the JetBrains SDK or IntelliJ in any way
- They cannot depend on any google proprietary infrastructure or APIs

The libraries may be hosted inside an IJ plugin or a cloud service (or somewhere
else). See go/ij-sync-prototype for more context.

Important: These libraries are currently prototypes and should not be depended
on by any production code.
