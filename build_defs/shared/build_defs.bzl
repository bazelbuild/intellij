"""Custom build macros for IntelliJ plugin handling.
"""

def merged_plugin_xml_impl(name, srcs, merge_tool, **kwargs):
  """Merges N plugin.xml files together."""
  native.genrule(
      name = name,
      srcs = srcs,
      outs = [name + ".xml"],
      cmd = "./$(location {merge_tool}) $(SRCS) > $@".format(
          merge_tool=merge_tool,
      ),
      tools = [merge_tool],
      **kwargs)

def _optstr(name, value):
  return ("--" + name) if value else ""

def stamped_plugin_xml_impl(name,
                            plugin_xml,
                            api_version_txt,
                            stamp_tool,
                            plugin_id = None,
                            plugin_name = None,
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
    api_version_txt: the file containing the api version
    stamp_tool: the tool to use to stamp the version
    plugin_id: the plugin ID to stamp
    plugin_name: the plugin name to stamp
    stamp_since_build: Add build number to idea-version since-build.
    stamp_until_build: Add build number to idea-version until-build.
    include_product_code_in_stamp: Whether the product code (eg. "IC")
        is included in since-build and until-build.
    version_file: A file with the version number to be included.
    changelog_file: A file with the changelog to be included.
    description_file: A file containing a plugin description to be included.
    vendor_file: A file containing the vendor info to be included.
    **kwargs: Any additional arguments to pass to the final target.
  """
  args = [
      "./$(location {stamp_tool})",
      "--plugin_xml=$(location {plugin_xml})",
      "--api_version_txt=$(location {api_version_txt})",
      "{stamp_since_build}",
      "{stamp_until_build}",
      "{include_product_code_in_stamp}",
  ]
  srcs = [plugin_xml, api_version_txt]

  if plugin_id:
    args.append("--plugin_id=%s" % plugin_id)

  if plugin_name:
    args.append("--plugin_name='%s'" % plugin_name)

  if version_file:
    args.append("--version_file=$(location {version_file})")
    srcs.append(version_file)

  if changelog_file:
    args.append("--changelog_file=$(location {changelog_file})")
    srcs.append(changelog_file)

  if description_file:
    args.append("--description_file=$(location {description_file})")
    srcs.append(description_file)

  if vendor_file:
    args.append("--vendor_file=$(location {vendor_file})")
    srcs.append(vendor_file)

  cmd = " ".join(args).format(
      plugin_xml=plugin_xml,
      api_version_txt=api_version_txt,
      stamp_tool=stamp_tool,
      stamp_since_build=_optstr("stamp_since_build",
                                stamp_since_build),
      stamp_until_build=_optstr("stamp_until_build",
                                stamp_until_build),
      include_product_code_in_stamp=_optstr(
          "include_product_code_in_stamp",
          include_product_code_in_stamp),
      version_file=version_file,
      changelog_file=changelog_file,
      description_file=description_file,
      vendor_file=vendor_file,
  ) + "> $@"

  native.genrule(
      name = name,
      srcs = srcs,
      outs = [name + ".xml"],
      cmd = cmd,
      tools = [stamp_tool],
      **kwargs)

def product_build_txt_impl(name,
                           product_build_txt_tool,
                           application_info_jar,
                           application_info_name,
                           **kwargs):
  """Produces a product-build.txt file with the build number.

  Args:
    name: name of this target
    product_build_txt_tool: the product build tool
    application_info_jar: the jar containing the application info
    application_info_name: a file with the name of the application info
    **kwargs: Any additional arguments to pass to the final target.
  """
  args = [
      "./$(location {product_build_txt_tool})",
      "--application_info_jar=$(location {application_info_jar})",
      "--application_info_name=$(location {application_info_name})",
  ]
  cmd = " ".join(args).format(
      application_info_jar=application_info_jar,
      application_info_name=application_info_name,
      product_build_txt_tool=product_build_txt_tool,
  ) + "> $@"
  native.genrule(
      name = name,
      srcs = [application_info_jar, application_info_name],
      outs = ["product-build.txt"],
      cmd = cmd,
      tools = [product_build_txt_tool],
      **kwargs)

def api_version_txt_impl(name,
                         api_version_txt_tool,
                         application_info_jar,
                         application_info_name,
                         **kwargs):
  """Produces an api_version.txt file with the api version, including the product code.

  Args:
    name: name of this target
    api_version_txt_tool: the api version tool
    application_info_jar: the jar containing the application info
    application_info_name: a file with the name of the application info
    **kwargs: Any additional arguments to pass to the final target.
  """
  args = [
      "./$(location {api_version_txt_tool})",
      "--application_info_jar=$(location {application_info_jar})",
      "--application_info_name=$(location {application_info_name})",
  ]
  cmd = " ".join(args).format(
      application_info_jar=application_info_jar,
      application_info_name=application_info_name,
      api_version_txt_tool=api_version_txt_tool,
  ) + "> $@"
  native.genrule(
      name = name,
      srcs = [application_info_jar, application_info_name],
      outs = [name + ".txt"],
      cmd = cmd,
      tools = [api_version_txt_tool],
      **kwargs)

def intellij_plugin_impl(name,
                         deps,
                         plugin_xml,
                         zip_tool,
                         meta_inf_files=[],
                         **kwargs):
  """Creates an intellij plugin from the given deps and plugin.xml."""
  binary_name = name + "_binary"
  deploy_jar = binary_name + "_deploy.jar"
  native.java_binary(
      name = binary_name,
      runtime_deps = deps,
      create_executable = 0,
  )
  cmd = [
      "cp $(location {deploy_jar}) $@".format(deploy_jar=deploy_jar),
      "chmod +w $@",
      "mkdir -p META-INF",
      "cp $(location {plugin_xml}) META-INF/plugin.xml".format(plugin_xml=plugin_xml),
      "$(location {zip_tool}) -u $@ META-INF/plugin.xml >/dev/null".format(zip_tool=zip_tool),
  ]
  srcs = [
      plugin_xml,
      deploy_jar,
  ]

  for meta_inf_file in meta_inf_files:
    cmd.append("cp $(location {meta_inf_file}) META-INF/$$(basename $(location {meta_inf_file}))".format(
        meta_inf_file=meta_inf_file,
    ))
    cmd.append("$(location {zip_tool}) -u $@ META-INF/$$(basename $(location {meta_inf_file})) >/dev/null".format(
        zip_tool=zip_tool,
        meta_inf_file=meta_inf_file,
    ))
    srcs.append(meta_inf_file)

  native.genrule(
      name = name + "_genrule",
      srcs = srcs,
      tools = [zip_tool],
      outs = [name + ".jar"],
      cmd = " ; ".join(cmd),
  )

  # included (with tag) as a hack so that IJwB can recognize this is an intellij plugin
  native.java_import(
      name = name,
      jars = [name + ".jar"],
      tags = ["intellij-plugin"],
      **kwargs)

def plugin_bundle_impl(name, plugins):
  """Communicates to IJwB a set of plugins which should be loaded together in a run configuration.

  Args:
    name: the name of this target
    plugins: the 'intellij_plugin' targets to be bundled
  """
  native.java_library(
      name = name,
      exports = plugins,
      tags = ["intellij-plugin-bundle"],
  )
