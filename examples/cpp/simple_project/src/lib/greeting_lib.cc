#include "src/lib/greeting_lib.h"
#include <string>

std::string get_greet(const std::vector<std::string>& who) {
    std::string result = "Hello";
    for (std::string name : who) {
        result.append(" ");
        result.append(name );
    }
  return result;
}
