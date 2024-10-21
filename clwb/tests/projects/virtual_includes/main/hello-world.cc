#include "strip_absolut/strip_absolut.h"
#include "strip_absolut/generated.h"
#include "strip_relative.h"
#include "lib/impl_deps/impl.h"

int main() {
  strip_absolut_function();
  strip_relative_function();
  impl_deps_function();

  return GENERATED_MACRO;
}
