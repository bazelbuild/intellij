Plugin to sync settings for the [protostuff intellij proto plugin](https://github.com/protostuff/protobuf-jetbrains-plugin)

## Behavior

If the plugin is available in the workspace: and If the canonical
`com_google_protobuf` workspace is found add it to the import path so
that the WKT's may be discovered.

Provide a `proto_import_roots` directory section to add additional
import paths. If the view set contains any such entries offer to install
the plugin.