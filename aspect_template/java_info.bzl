def java_info_in_target(target):
    return JavaInfo in target

def get_java_info(target):
    if JavaInfo in target:
        return target[JavaInfo]
    else:
        None

def java_info_reference():
    return [JavaInfo]
