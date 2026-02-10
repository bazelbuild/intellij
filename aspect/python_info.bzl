# TEMPLATE-INCLUDE-BEGIN
###if( $isPythonEnabled == "true" )
##load("@rules_python//python:defs.bzl", RulesPyInfo = "PyInfo")
###end
# TEMPLATE-INCLUDE-END

def py_info_in_target(target):
# TEMPLATE-IGNORE-BEGIN
    return PyInfo in target
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
###if( $isPythonEnabled == "true" )
##    if RulesPyInfo in target:
##        return True
###end
###if( $bazel8OrAbove != "true")
##    if PyInfo in target:
##        return True
###end
##    return False
# TEMPLATE-INCLUDE-END

def get_py_info(target):
# TEMPLATE-IGNORE-BEGIN
    if PyInfo in target:
        return target[PyInfo]
    else:
        return None
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
###if( $isPythonEnabled == "true" )
##    if RulesPyInfo in target:
##        return target[RulesPyInfo]
###end
###if( $bazel8OrAbove != "true")
##    if PyInfo in target:
##        return target[PyInfo]
###end
##    return None
# TEMPLATE-INCLUDE-END

