VERSIONS = {
%{versions_entries}
}

def resolve(major):
    """Resolves a major version integer to a struct(version, label)."""
    if major not in VERSIONS:
        fail("Unknown Bazel major version: %d. Available: %s" % (major, sorted(VERSIONS.keys())))
    return VERSIONS[major]
