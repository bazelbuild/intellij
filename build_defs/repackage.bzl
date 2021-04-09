"""Repackages jar files using Jar Jar Links."""

_JDK_CLASSES = [
    "com.sun.",
    "java.",
    # don't include javax.annotation.
    "javax.accessibility.",
    "javax.swing.",
]

_JETBRAINS_CLASSES = [
    "com.android.",
    "com.goide.",
    "com.intellij.",
    "com.jetbrains.",
    "jetbrains.",
    "org.intellij.",
    "org.jetbrains.",
    "org.zmlx.hg4idea.",
    # TODO(b/188322932): remove
    # the following are not actually jetbrains classes
    # but we must use the versions of these from jetbrains because
    # 1) it's part of their API, or
    # 2) they don't exist in google3
    # change references to these to _jetbrains_.* to make it obvious
    # and remove the entries here
    "com.github.markusbernhardt.",
    "com.google.devrel.gmscore.tools.apk.",
    "com.google.gct.testrecorder.",
    "com.google.wireless.android.sdk.",
    "gnu.trove.",
    "kotlin.",
    "one.util.streamex.",
    "org.angular2.",
    "org.apache.commons.httpclient.",
    "org.apache.commons.lang.",
    "org.apache.commons.lang3.",
    "org.apache.log4j.",
    "org.jdom.",
    "org.picocontainer.",
    "scala.",
]

_OUR_CLASSES = [
    "com.android.",
    "com.google.devtools.intellij.",
    "com.google.idea.",
    "com.google.org.jetbrains.",
    "icons.",
]

def repackaged_jetbrains_jars(name, jars, **kwargs):
    """Repackages jetbrains jars with hardcoded rules.

    Args:
        name: Name of the resulting java_import target.
        jars: List of jars to repackage.
        **kwargs: Arbitrary attributes for the java_import target.
    """

    # don't touch jdk or jetbrains classes
    # NOTE: entry order matters, no-op transforms must come first
    rules = {c: c for c in _JDK_CLASSES + _JETBRAINS_CLASSES}

    # repackage everything else from the IDE
    rules[""] = "_jetbrains_."

    _repackaged_jars(
        name = name,
        jars = jars,
        rules = rules,
        **kwargs
    )

def repackaged_plugin_jars(name, jars, **kwargs):
    """Repackages plugin jars by undoing the jetbrains rules.

    Args:
        name: Name of the resulting java_import target.
        jars: List of jars to repackage.
        **kwargs: Arbitrary attributes for the java_import target.
    """

    # don't touch jdk, jetbrains, or our own classes
    # NOTE: entry order matters, no-op transforms must come first
    rules = {c: c for c in _JDK_CLASSES + _JETBRAINS_CLASSES + _OUR_CLASSES}

    # undo repackaging on classes from the IDE
    rules["_jetbrains_."] = ""

    # repackage everything else from google3
    rules[""] = "_google_."

    _repackaged_jars(
        name = name,
        jars = jars,
        rules = rules,
        **kwargs
    )

def repackaged_deploy_jar(name, input_jar, output, visibility):
    repackaged_name = name + "_repackaged"
    repackaged_plugin_jars(
        name = repackaged_name,
        jars = [input_jar],
    )
    native.genrule(
        name = name,
        srcs = [repackaged_name + "/" + input_jar.replace(":", "")],
        outs = [output],
        cmd = "cp $< $@",
        visibility = visibility,
    )

def _to_jarjar_rules(rules):
    jarjar_rules = ["rule META-INF.** META-INF.@1"]  # don't move META-INF
    for old, new in rules.items():
        if old == "":
            # jarjar doesn't allow ** pattern by itself
            jarjar_rules.append("rule *.** {new}@1.@2".format(new = new))
        else:
            jarjar_rules.append("rule {old}** {new}@1".format(old = old, new = new))
    return "\n".join(jarjar_rules)

def _to_mv_commands(rules):
    # move after modifying so only one rule is applied per file
    commands = ["mkdir modified"]
    for old, new in rules.items():
        commands.append(
            """
            for file in {old}*; do
                if [[ -f $$file ]]; then
                    mv $$file modified/{new}$${{file#{old}}}
                fi
            done
            """.format(new = new, old = old),
        )
    commands += [
        "mv modified/* .",
        "rmdir modified",
    ]
    return "\n".join(commands)

def _to_sed_command(rules):
    # mark each line so we only modify them once
    replacements = ["-e s/^/unmodified@/"]
    for old, new in rules.items():
        old = old.replace(".", "\\.")
        replacements.append(
            "-e s/^unmodified@{old}/{new}/".format(old = old, new = new),
        )
    replacements.append("-e s/^unmodified@//")
    return "sed -i {} *".format(" ".join(replacements))

def _repackaged_jars(name, jars, rules, **kwargs):
    """Repackages jar files using Jar Jar Links.

    Args:
        name: Name of the resulting java_import target.
        jars: List of jars to repackage.
        rules: Map of package prefix replacements.
        **kwargs: Arbitrary attributes for the java_import target.
    """

    jar = "//third_party/java/jdk/jar"
    jarjar = "@jarjar//:jarjar_bin"

    output_jars = []
    for input_jar in jars:
        output_jar_name = name + "_" + input_jar.replace(":", "").replace("/", "_")
        output_jar = name + "/" + input_jar.replace(":", "")
        native.genrule(
            name = output_jar_name,
            srcs = [input_jar],
            outs = [output_jar],
            cmd = """
            jar=$$PWD/$(location {jar})
            jarjar=$$PWD/$(location {jarjar})
            in=$$PWD/$<
            out=$$PWD/$@

            mkdir $@.tmp
            pushd $@.tmp > /dev/null

            # extract everything to get rid of duplicate files
            $$jar xf $$in

            # handle class references in META-INF/services: http://b/119045995
            if ls META-INF/services/* &>/dev/null; then
                pushd META-INF/services > /dev/null
                {renames}
                {replacements}
                popd > /dev/null
            fi

            # delete mockito extensions: http://b/183925215
            rm -rf mockito-extensions/

            # delete signatures
            rm -rf META-INF/*.SF META-INF/*.RSA

            # put everything back together
            $$jar cf $$out .

            popd > /dev/null
            rm -r $@.tmp

            # finally do the actual repackaging
            $$jarjar process <(printf %s "{jarjar_rules}") $$out $$out
            """.format(
                jar = jar,
                jarjar = jarjar,
                renames = _to_mv_commands(rules),
                replacements = _to_sed_command(rules),
                jarjar_rules = _to_jarjar_rules(rules),
            ),
            tools = [jar, jarjar],
        )
        output_jars.append(output_jar_name)

    native.java_import(
        name = name,
        jars = output_jars,
        **kwargs
    )

# TODO(b/188322932): remove
def repackaged_jar(name, rules, deps, **kwargs):
    native.java_library(
        name = name,
        exports = deps,
        **kwargs
    )
