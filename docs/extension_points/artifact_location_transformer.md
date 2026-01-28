# Artifact Location Transformer

The `ArtifactLocationTransformer` extension point allows you to transform `ArtifactLocation` objects during sync, enabling customization of how artifact locations are processed when converting from proto to the IDE model.

## How to Use

### 1. Implement the Interface

Create a class implementing `com.google.idea.blaze.base.ideinfo.ArtifactLocationTransformer`:

```java
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ArtifactLocationTransformer;
public class CffArtifactTransformer implements ArtifactLocationTransformer {
  
  @Override
  public ArtifactLocation transform(ArtifactLocation artifact) {
    String path = artifact.getRelativePath();
    
    if (path.endsWith("_cffgen.go")) {
      String newPath = path
          .replace("cff_", "")
          .replace("_cffgen.go", ".go")
      
      return new ArtifactLocation.Builder()
          .setRelativePath(newPath)
          .setIsSource(true)
          .setIsExternal(false)
          .build();
    }
    
    return artifact;
  }
}
```

### 2. Register the Extension

Add the extension to your plugin's `plugin.xml`:

```xml
<idea-plugin>
  <extensions defaultExtensionNs="com.google.idea.blaze">
    <ArtifactLocationTransformer 
        implementation="com.example.myproject.CffArtifactTransformer" />
  </extensions>
</idea-plugin>
```

### 3. Build and Test

After registering your transformer, rebuild your plugin and run a sync. The transformer will be applied to **all** artifacts during proto-to-model conversion. Use file extension or path pattern checks to filter which artifacts your transformer modifies.

## Notes

- Transformers are applied in registration order during `ArtifactLocation.fromProto()`
- Multiple transformers can be chained (each receives the output of the previous)
- Use file extension or path patterns to filter which files your transformer processes
- Transformations apply to all language classes - filter by checking file extensions (`.go`, `.py`, `.java`, etc.)
- Transformation happens once during proto deserialization, so all downstream code sees the transformed artifacts
