# TEMPLATE-INCLUDE-BEGIN
###if( $isCcEnabled == "true" && $bazel9OrAbove == "true" )
##load("@rules_cc//cc:defs.bzl", "CcInfo", "cc_common")
###end
##
##CC_USE_GET_TOOL_FOR_ACTION = ${use_get_tool_for_action}
# TEMPLATE-INCLUDE-END

# TEMPLATE-IGNORE-BEGIN
load("@rules_cc//cc:defs.bzl", "CcInfo", "cc_common")

CC_USE_GET_TOOL_FOR_ACTION = True

CcInfoCompat = CcInfo
cc_common_compat = cc_common
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
## #if( $isCcEnabled == "true")
##CcInfoCompat = CcInfo
##cc_common_compat = cc_common
## #else
##CcInfoCompat = None
##cc_common_compat = None
##  #end
# TEMPLATE-INCLUDE-END

def cc_info_reference():
# TEMPLATE-IGNORE-BEGIN
    return [CcInfoCompat]

# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
## #if( $isCcEnabled == "true")
##  return [CcInfoCompat]
## #else
##  return []
##  #end
# TEMPLATE-INCLUDE-END

def cc_info_in_target(target):
# TEMPLATE-IGNORE-BEGIN
    return CcInfoCompat in target

# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if($isCcEnabled == "true")
##  return CcInfoCompat in target
##  #else
##  return None
##  #end
# TEMPLATE-INCLUDE-END

def get_cc_info(target):
# TEMPLATE-IGNORE-BEGIN
    if CcInfoCompat in target:
        return target[CcInfoCompat]
    else:
        return None

# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isCcEnabled == "true")
##  if CcInfoCompat in target:
##      return target[CcInfoCompat]
##  else:
##      return None
##  #else
##  return None
##  #end
# TEMPLATE-INCLUDE-END
