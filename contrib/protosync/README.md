Plugin to sync settings for the [protostuff intellij proto plugin](https://github.com/protostuff/protobuf-jetbrains-plugin)

## Behavior

if any `proto_import_roots` sections are found and the plugin is missing
add an information issue to install the plugin.

Add workspace directories to the protobuf plugin so that it may resolve
types correctly.

valid `proto_import_roots`:
```
proto_import_roots:
  @com_google_protobuf//src
  proto/axsy
  //proto/axsy
```