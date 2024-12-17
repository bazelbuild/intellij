provider_attrs = ["xcode_version", "default_macos_sdk_version"]

def alltrue(items):
    result = True
    for item in items:
        result = result and item

    return result

def hasattrs(obj, attrs):
    return alltrue([hasattr(obj, attr) for attr in attrs])

def format(target):
    all_providers = providers(target)
    for key in all_providers:
        provider = all_providers[key]

        if hasattrs(provider, provider_attrs):
            attrs = [getattr(provider, attr) for attr in provider_attrs]
            return "{} {}".format(attrs[0], attrs[1])

    return ""
