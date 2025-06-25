# TEMPLATE-IGNORE-BEGIN
###if( $isJavaEnabled == "true" && $bazel8OrAbove == "true" )
##load("@rules_java//java/common:java_info.bzl", "JavaInfo")
##load("@rules_java//java/common:java_common.bzl", "java_common")
###end
# TEMPLATE-IGNORE-END

def java_info_in_target(target):
# TEMPLATE-IGNORE-BEGIN
    return JavaInfo in target
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isJavaEnabled == "true" || $isTransitiveJava == "true" )
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


def get_provider_from_target(provider_name, target):
# TEMPLATE-IGNORE-BEGIN
    provider = getattr(java_common, provider_name, None)
    return target[provider] if provider and provider in target else None
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isJavaEnabled == "true" )
##  provider = getattr(java_common, provider_name, None)
##  return target[provider] if provider and provider in target else None
##  #else
##  return None
##  #end
# TEMPLATE-INCLUDE-END