Plugin to sync settings for the [protostuff intellij proto plugin](https://github.com/protostuff/protobuf-jetbrains-plugin)

## Behavior

1. If any `proto_import_roots` sections are found and the plugin is missing add an information issue to install the plugin.
2. Convert any entries found in `proto_import_roots` to filesystem locations.
3. For declared root that is verified to exist add it to the Protobuf plugin configuration. If the directory was not 
    yet fetched by bazel raise a note and ignore. Eventually once the user has primed their workspace the directories 
    should be added.  

valid `proto_import_roots`:
```
proto_import_roots:
  @com_google_protobuf//src
  proto/axsy
  //proto/axsy
```