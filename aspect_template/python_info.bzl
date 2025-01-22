# TEMPLATE-INCLUDE-BEGIN
###if( $isPythonEnabled == "true" && $bazel8OrAbove == "true" )
##load("@rules_python//python:defs.bzl", "PyInfo")
###end
# TEMPLATE-INCLUDE-END

def py_info_in_target(target):
# TEMPLATE-IGNORE-BEGIN
    return PyInfo in target
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isPythonEnabled == "true" )
##  return PyInfo in target
##  #else
##  return None
##  #end
# TEMPLATE-INCLUDE-END

def get_py_info(target):
# TEMPLATE-IGNORE-BEGIN
    if PyInfo in target:
        return target[PyInfo]
    else:
        return None
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isPythonEnabled == "true" )
##  if PyInfo in target:
##      return target[PyInfo]
##  else:
##      return None
##  #else
##  return None
##  #end
# TEMPLATE-INCLUDE-END