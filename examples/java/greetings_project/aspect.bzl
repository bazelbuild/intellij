load("@rules_java//java:defs.bzl", "JavaInfo")

def _impl(target, ctx):
    if JavaInfo in target:
        print("has JavaInfo")
    else:
        print("no JavaInfo")
    return []
    
my_aspect = aspect(implementation = _impl)
