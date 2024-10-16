## Requirements
In order to get fast build working, you have to add some VM options to the IntelliJ setup.
You can go to Help -> Edit custom VM options, and add these entries there:
```
--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
```
