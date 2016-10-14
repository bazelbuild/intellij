"""Custom build macros for plugin.xml handling.
"""

load("//build_defs/shared:build_defs.bzl",
     "merged_plugin_xml_impl",
     "stamped_plugin_xml_impl",
     "product_build_txt_impl",
     "api_version_txt_impl",
     "intellij_plugin_impl",
     "plugin_bundle_impl")

def merged_plugin_xml(name, srcs, **kwargs):
  """Merges N plugin.xml files together."""
  merged_plugin_xml_impl(
      name = name,
      srcs = srcs,
      merge_tool = "//build_defs/shared:merge_xml",
      **kwargs)

def stamped_plugin_xml(name,
                       plugin_xml,
                       plugin_id=None,
                       plugin_name=None,
                       stamp_since_build=False,
                       stamp_until_build=False,
                       include_product_code_in_stamp=False,
                       version_file=None,
                       changelog_file=None,
                       description_file=None,
                       vendor_file=None,
                       **kwargs):
  """Stamps a plugin xml file with the IJ build number.

  Args:
    name: name of this target
    plugin_xml: target plugin_xml to stamp
    plugin_id: the plugin ID to stamp
    plugin_name: the plugin name to stamp
    stamp_since_build: Add build number to idea-version since-build.
    stamp_until_build: Add build number to idea-version until-build.
    include_product_code_in_stamp: Whether the product code (eg. "IC")
      is included in since-build and until-build.
    version_file: A file with the version number to be included.
    changelog_file: A file with changelog to be included.
    description_file: A file containing a plugin description to be included.
    vendor_file: A file containing the vendor info to be included.
    **kwargs: Any additional arguments to pass to the final target.
  """
  api_version_txt(
      name = name + "_api_version",
  )
  stamped_plugin_xml_impl(
      name = name,
      api_version_txt = name + "_api_version",
      plugin_id = plugin_id,
      plugin_name = plugin_name,
      stamp_tool = "//build_defs/shared:stamp_plugin_xml",
      plugin_xml = plugin_xml,
      stamp_since_build = stamp_since_build,
      stamp_until_build = stamp_until_build,
      include_product_code_in_stamp = include_product_code_in_stamp,
      version_file = version_file,
      changelog_file = changelog_file,
      description_file = description_file,
      vendor_file = vendor_file,
      **kwargs)

def product_build_txt(name, **kwargs):
  """Produces a product-build.txt file with the build number."""
  product_build_txt_impl(
      name = name,
      application_info_jar = "//intellij_platform_sdk:application_info_jar",
      application_info_name = "//intellij_platform_sdk:application_info_name",
      product_build_txt_tool = "//build_defs/shared:product_build_txt",
      **kwargs)

def api_version_txt(name, **kwargs):
  """Produces an api_version.txt file with the api version, including the product code."""
  api_version_txt_impl(
      name = name,
      application_info_jar = "//intellij_platform_sdk:application_info_jar",
      application_info_name = "//intellij_platform_sdk:application_info_name",
      api_version_txt_tool = "//build_defs/shared:api_version_txt",
      **kwargs)

def intellij_plugin(name, plugin_xml, deps, meta_inf_files=[], **kwargs):
  """Creates an intellij plugin from the given deps and plugin.xml."""
  intellij_plugin_impl(
      name = name,
      plugin_xml = plugin_xml,
      zip_tool = "//third_party:zip",
      deps = deps,
      meta_inf_files = meta_inf_files,
      **kwargs)

def repackage_jar(name, src_rule, out,
                  rules = [
                      "com.google.common.** com.google.repackaged.common.@1",
                      "com.google.gson.** com.google.repackaged.gson.@1",
                      "com.google.protobuf.** com.google.repackaged.protobuf.@1",
                      "com.google.thirdparty.** com.google.repackaged.thirdparty.@1",
                  ], **kwargs):
  """Repackages classes in a jar, to avoid collisions in the classpath.

  Args:
    name: the name of this target
    src_rule: a java_binary label with the create_executable attribute set to 0
    out: the output jarfile
    rules: the rules to apply in the repackaging
        Only repackage some of com.google.** from proto_deps.jar.
        We do not repackage:
        - com.google.net.** because that has JNI files which use
          FindClass(JNIEnv *, const char *) with hard-coded native string
          literals that jarjar doesn't rewrite.
        - com.google.errorprone packages (rewriting will throw off blaze build).
    **kwargs: Any additional arguments to pass to the final target.
  """
  repackage_tool = "@jarjar//jar"
  deploy_jar = "{src_rule}_deploy.jar".format(src_rule=src_rule)
  script_lines = []
  script_lines.append("echo >> /tmp/repackaged_rule.txt")
  for rule in rules:
    script_lines.append("echo 'rule {rule}' >> /tmp/repackaged_rule.txt;".format(rule=rule))
  script_lines.append(" ".join([
      "$(location {repackage_tool})",
      "process /tmp/repackaged_rule.txt",
      "$(location {deploy_jar})",
      "$@",
  ]).format(
      repackage_tool = repackage_tool,
      deploy_jar = deploy_jar,
  ))
  genrule_name = name + "_gen"
  native.genrule(
      name = genrule_name,
      srcs = [deploy_jar],
      outs = [out],
      tools = [repackage_tool],
      cmd = "\n".join(script_lines),
  )
  native.java_import(
      name = name,
      jars = [out],
      **kwargs)

def plugin_bundle(name, plugins):
  """Communicates to IJwB a set of plugins which should be loaded together in a run configuration.

  Args:
    name: the name of this target
    plugins: the 'intellij_plugin' targets to be bundled
  """
  plugin_bundle_impl(name, plugins)
