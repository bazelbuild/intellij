#include "strip_absolut/strip_absolut.h"
#include "strip_absolut/generated.h"
#include "strip_relative.h"

int main() {
  strip_absolut_function();
  strip_relative_function();

  return GENERATED_MACRO;
}
