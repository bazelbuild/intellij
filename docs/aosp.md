# ASwB as a Part of AOSP and This Repo

For a long time, the Android Studio plugin was built from the 
[google branch](https://github.com/bazelbuild/intellij/tree/google), and the
[master branch](https://github.com/bazelbuild/intellij/tree/master) was receiving updates from the former as cherry-picks. After the migration of the Android Studio plugin to 
[AOSP](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/aswb/),
this process was interrupted for approximately five months.

In November 2024, many changes from AOSP were cherry-picked in 
[6965](https://github.com/bazelbuild/intellij/pull/6965). 
Starting from that point, we have been making our 
best effort to apply AOSP changes to the 
[master branch](https://github.com/bazelbuild/intellij/tree/master).

For this purpose, we use the Python tool 
[LeFrosch/intellij-aosp-merge](https://github.com/LeFrosch/intellij-aosp-merge), 
which is capable of adjusting paths from AOSP to this project's 
layout and allows us to compare revisions in AOSP and here.  