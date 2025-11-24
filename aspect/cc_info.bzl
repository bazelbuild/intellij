# TEMPLATE-INCLUDE-BEGIN
## #if( $isCcEnabled == "true" )
##load("@rules_cc//cc:defs.bzl", "CcInfo", "cc_common")
## #end
##
##CC_USE_GET_TOOL_FOR_ACTION = ${use_get_tool_for_action}
# TEMPLATE-INCLUDE-END

# TEMPLATE-IGNORE-BEGIN
load("@rules_cc//cc:defs.bzl", "CcInfo", "cc_common")

CC_USE_GET_TOOL_FOR_ACTION = True
# TEMPLATE-IGNORE-END

CcInfoCompat = CcInfo
cc_common_compat = cc_common
