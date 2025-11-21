# TEMPLATE-INCLUDE-BEGIN
## #if( $isCcEnabled == "true" )
##load("@rules_cc//cc:defs.bzl", _CcInfo = "CcInfo", _cc_common = "cc_common")
## #end
##
##CC_USE_GET_TOOL_FOR_ACTION = ${use_get_tool_for_action}
##
## #if( $isCcEnabled == "true" )
##CcInfoCompat = _CcInfo
##cc_common_compat = _cc_common
## #else
##CcInfoCompat = CcInfo
##cc_common_compat = cc_common
## #end
# TEMPLATE-INCLUDE-END

# TEMPLATE-IGNORE-BEGIN
load("@rules_cc//cc:defs.bzl", _CcInfo = "CcInfo", _cc_common = "cc_common")

CcInfoCompat = _CcInfo
cc_common_compat = _cc_common

CC_USE_GET_TOOL_FOR_ACTION = True
# TEMPLATE-IGNORE-END
