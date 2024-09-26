def get_java_provider(target):
    """Find a provider exposing java compilation/outputs data."""

    # Check for kt providers before JavaInfo. e.g. kt targets have
    # JavaInfo, but their data lives in the "kt" provider and not JavaInfo.
    # See https://github.com/bazelbuild/intellij/pull/1202
    if hasattr(target, "kt") and hasattr(target.kt, "outputs"):
        return target.kt
    if JavaInfo in target:
        return target[JavaInfo]
    if hasattr(java_common, "JavaPluginInfo") and java_common.JavaPluginInfo in target:
        return target[java_common.JavaPluginInfo]
    return None
