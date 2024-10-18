# simple_code_generator Python Example

This simple project is intended to facilitate demonstration of Intelli-J working with a Bazel Python project that uses code-generator Bazel Rules.

## Run the example

```
bazel run "//example:bin"
```

The expected output from the example is;

```
- Auckland City
- Regensburg City
- Darwin City
- Toulouse City
* Whanganui River
* Danube River
* Yarra River
* Volga River
# Bakerlite Plastic
# Polyethylene Plastic
# Nylon Plastic
```

## Explanation

The example is a `py_binary` with three `deps` and which, via `main.py`, uses a function from each of the `deps` to print the list of cities, rivers and plastics above.

- `//lib:static_lib` provides the list of rivers and is sourced from checked-in source files.
- `//generated:generated_files_lib` provides the list of cities and is sourced from a code-generator that outputs individual source files.
- `//generated:generated_directory_lib` provides the list of plastics and is sourced from a code-generator that outputs a directory containing source files.

In each case, `imports` are used.

## Key observations

Open the project in Intelli-J ensuring that Python is enabled as a language option and that the `tools/intellij/.managed.bazelproject` is used to setup the project's view. Perform a sync on the project. Open the `main.py` file. Observe that the imports and symbols for each of the three `deps` is recognized by the IDE. The project view has a section...

```
python_code_generator_rule_names:
  test_codegen_files_py
  test_codegen_directory_py
```

...which is instructing the IDE to consider `test_codegen_files_py` and `test_codegen_directory_py` as code-generators.