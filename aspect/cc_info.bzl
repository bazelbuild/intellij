# TEMPLATE-IGNORE-BEGIN
load(":cc_dependencies.bzl", _collect_cc_deps = "collect_cc_deps")
CC_USE_GET_TOOL_FOR_ACTION = True
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
## #if( $collect_dependencies )
##load(":cc_dependencies.bzl", _collect_cc_deps = "collect_cc_deps")
## #else
##_collect_cc_deps = None
## #end
# TEMPLATE-INCLUDE-END

# TEMPLATE-INCLUDE-BEGIN
##CC_USE_GET_TOOL_FOR_ACTION = ${use_get_tool_for_action}
# TEMPLATE-INCLUDE-END

collect_cc_deps = _collect_cc_deps