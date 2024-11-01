# Python code generators

## Background

It is possible to create a _code generator_ Bazel Rule that acts as a `py_library`. The Bazel Action setup by the code generator Rule would execute a tool that outputs Python source code. A `py_binary` is then configured with the code generator Rule instance as a dependency and uses the generated source code. 

The following diagram shows how a code generator might look like in a typical scenario where code is generated from the structures present in a schema.

![Code generators graph](images/code-generators.svg)

## Example

See the Python example [simple_code_generator](/examples/python/simple_code_generator).

## Intelli-J

The Bazel Intelli-J plugin is able to work with generated Python code in this situation. You need to ensure that your code generator Bazel Rule has no `srcs` attribute and that it is configured in your `.bazelproject` file as follows...

```
python_code_generator_rule_names:
  test_codegen_files_py
  test_codegen_directory_py
```

...where `test_codegen_files_py` and `test_codegen_directory_py` are Rule names.