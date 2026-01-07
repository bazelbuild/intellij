# TEMPLATE-INCLUDE-BEGIN
###if( $isScalaEnabled == "true" )
##load("@rules_scala//scala:providers.bzl", "ScalaInfo")
###end
# TEMPLATE-INCLUDE-END

def scala_info_in_target(target):
    """Returns True if the target has ScalaInfo provider, indicating it's a Scala target."""
# TEMPLATE-IGNORE-BEGIN
    # Check if ScalaInfo provider exists (from rules_scala)
    # We use a string-based check to avoid hard dependency on rules_scala
    for provider in dir(target):
        if "ScalaInfo" in str(provider):
            return True
    # Fallback: check if any provider's type name contains "ScalaInfo"
    for p in target:
        if "ScalaInfo" in str(type(p)):
            return True
    return False
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##  #if( $isScalaEnabled == "true" )
##  return ScalaInfo in target
##  #else
##  return False
##  #end
# TEMPLATE-INCLUDE-END
