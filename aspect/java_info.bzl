# TEMPLATE-INCLUDE-BEGIN
###if( $isJavaEnabled == "true" && $bazel8OrAbove == "true" )
##load("@rules_java//java/common:java_info.bzl", "JavaInfo")
###end
# TEMPLATE-INCLUDE-END

def java_info_in_target(target):
# TEMPLATE-IGNORE-BEGIN
    return JavaInfo in target
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isJavaEnabled == "true" )
##  return JavaInfo in target
##  #else
##  return None
##  #end
# TEMPLATE-INCLUDE-END

def get_java_info(target):
# TEMPLATE-IGNORE-BEGIN
    if JavaInfo in target:
        return target[JavaInfo]
    else:
        return None
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isJavaEnabled == "true" )
##  if JavaInfo in target:
##      return target[JavaInfo]
##  else:
##      return None
##  #else
##  return None
##  #end
# TEMPLATE-INCLUDE-END

def java_info_reference():
# TEMPLATE-IGNORE-BEGIN
    return [JavaInfo]
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isJavaEnabled == "true" )
##  return [JavaInfo]
##  #else
##  return []
##  #end
# TEMPLATE-INCLUDE-END
