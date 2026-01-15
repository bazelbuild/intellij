# TEMPLATE-INCLUDE-BEGIN
###if( $isScalaEnabled == "true" )
##load("@rules_scala//scala:providers.bzl", "ScalaInfo")
###end
# TEMPLATE-INCLUDE-END

def scala_info_in_target(target):
    """Returns True if the target has ScalaInfo provider, indicating it's a Scala target."""
# TEMPLATE-IGNORE-BEGIN
    return False
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
###if( $isScalaEnabled == "true" )
##  return ScalaInfo in target
###else
##  return False
###end
# TEMPLATE-INCLUDE-END
