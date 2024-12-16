# TEMPLATE-INCLUDE-BEGIN
###if( $isPythonEnabled == "true" )
##load("@rules_python//python:defs.bzl", RulesPyInfo = "PyInfo")
###end
# TEMPLATE-INCLUDE-END

def py_info_in_target(target):
# TEMPLATE-INCLUDE-BEGIN
###if( $isPythonEnabled == "true" )
##  if RulesPyInfo in target:
##    return True
###if( $bazel8OrAbove != "true")
##  if PyInfo in target:
##    return True
###end
###end
# TEMPLATE-INCLUDE-END
  return False

def get_py_info(target):
# TEMPLATE-INCLUDE-BEGIN
###if( $isPythonEnabled == "true" )
##  if RulesPyInfo in target:
##    return target[RulesPyInfo]
###if( $bazel8OrAbove != "true")
##  if PyInfo in target:
##    return target[PyInfo]
###end
###end
# TEMPLATE-INCLUDE-END
  return None

