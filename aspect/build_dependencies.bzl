"""Aspects to build and collect project dependencies."""

def zipper(target, ctx, input_depsets):
    """
    Creates the actions to zip the given inputs. Returns the zip file

    Args:
      target: the top level target.
      ctx: the context.
      input_depsets: the files to zip.
    Returns:
      The zip file.
    """
    inputs = input_depsets.to_list()
    zip_name = target.label.name
    zip_name += str(hash("//" + target.label.package + ":" + target.label.name))
    output_zip = ctx.actions.declare_file(zip_name + ".zip")

    args = ctx.actions.args()

    # using param file to get around argument length limitation
    # the name of param file (%s) is automatically filled in by blaze
    args.use_param_file("@%s")
    args.set_param_file_format("multiline")

    for i in inputs:
        args.add("%s=%s" % (i.short_path, i.path))

    ctx.actions.run(
        inputs = inputs,
        outputs = [output_zip],
        executable = ctx.executable._zipper,
        arguments = ["c", output_zip.path, args],
        progress_message = "Creating archive...",
        mnemonic = "archiver",
    )

    return output_zip

def _package_dependencies_impl(target, ctx):
    onezip = zipper(target, ctx, target[DependenciesInfo].compile_time_jars)
    return [OutputGroupInfo(ij_query_sync = [onezip])]

DependenciesInfo = provider(
    "The out-of-project dependencies",
    fields = ["compile_time_jars"],
)

package_dependencies = aspect(
    implementation = _package_dependencies_impl,
    attrs = {
        "_singlejar": attr.label(
            default = Label("//tools/jdk:singlejar"),
            cfg = "exec",
            executable = True,
            allow_files = True,
        ),
        "_zipper": attr.label(
            default = Label("//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
            allow_files = True,
        ),
    },
    required_aspect_providers = [[DependenciesInfo]],
)

def generates_idl_jar(target):
    if AndroidIdeInfo not in target:
        return False
    return target[AndroidIdeInfo].idl_class_jar != None

def _collect_dependencies_impl(target, ctx):
    label = str(target.label)
    included = False
    for inc in ctx.attr.include.split(","):
        if label.startswith(inc):
            if label[len(inc)] in [":", "/"]:
                included = True
                break
    if included and len(ctx.attr.exclude) > 0:
        for exc in ctx.attr.exclude.split(","):
            if label.startswith(exc):
                if label[len(exc)] in [":", "/"]:
                    included = False
                    break

    if included and ctx.rule.kind in ctx.attr.always_build_rules.split(","):
        included = False

    trs = []
    if not included:
        trs = [target[JavaInfo].compile_jars]
    for dep in ctx.rule.attr.deps:
        if DependenciesInfo in dep:
            trs.append(dep[DependenciesInfo].compile_time_jars)

    if included and ctx.attr.generate_aidl_classes and generates_idl_jar(target):
        idl_jar = target[AndroidIdeInfo].idl_class_jar
        trs.append(depset([idl_jar]))

    cj = depset([], transitive = trs)

    return [DependenciesInfo(compile_time_jars = cj)]

collect_dependencies = aspect(
    implementation = _collect_dependencies_impl,
    provides = [DependenciesInfo],
    attr_aspects = ["deps"],
    required_providers = [[JavaInfo]],
    attrs = {
        "include": attr.string(
            doc = "Comma separated list of workspace paths included in the project as source. Any targets inside here will not be built.",
            mandatory = True,
        ),
        "exclude": attr.string(
            doc = "Comma separated list of exclusions to 'include'.",
            default = "",
        ),
        "always_build_rules": attr.string(
            doc = "Comma separated list of rules. Any targets belonging to these rules will be built, regardless of location",
            default = "",
        ),
        "generate_aidl_classes": attr.bool(
            doc = "If True, generates classes for aidl files included as source for the project targets",
            default = False,
        ),
    },
)
