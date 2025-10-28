#include "strip_absolut/strip_absolut.h"
#include "strip_absolut/generated.h"
#include "strip_relative.h"
#include "lib/impl_deps/impl.h"
#include "raw_default.h"
#include "raw_system.h"
#include "raw_quote.h"
#include "external/generated.h"

int main() {
  strip_absolut_function();
  strip_relative_function();
  impl_deps_function();

  return GENERATED_MACRO + RAW_DEFAULT_MACRO + RAW_SYSTEM_MACRO + RAW_QUOTE_MACRO + EXTERNAL_GENERATED_MACRO;
}
