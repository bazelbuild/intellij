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

- `:static_lib` provides the list of rivers and is sourced from checked-in source files.
- `:generated_files_lib` provides the list of cities and is sourced from a code-generator that outputs individual source files.
- `:generated_directory_lib` provides the list of plastics and is sourced from a code-generator that outputs a directory containing source files.

In each case, `imports` are used.

## Key observations

Open the project in Intelli-J ensuring that Python is enabled as a language option. Perform a sync on the project. Open the `main.py` file. Observe that the imports and symbols for each of the three `deps` is recognized by the IDE. The targets `//example:generated_directory_lib` and `//example:generated_files_lib` have a tag `intellij-py-code-generator` which signals to the IDE that the target is a code generator.