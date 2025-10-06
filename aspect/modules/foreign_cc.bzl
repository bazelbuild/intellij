load("@rules_foreign_cc//foreign_cc:providers.bzl", "ForeignCcDepsInfo")

def module_foreign_cc_collect_dependencies(ctx, target):
    if ForeignCcDepsInfo not in target:
        return []

    return [
        struct(
            gen_dir = dep.gen_dir.path,
            include_dir_name = dep.include_dir_name,
        )
        for dep in target[ForeignCcDepsInfo].artifacts.to_list()
    ]
