#include <iostream>
#include <string>
#include <vector>
#include "src/lib/greeting_lib.h"

int main(int argc, char** argv) {
    std::vector<std::string> names;
    if (argc > 1) {
        names.push_back(argv[1]);
    } else {
        names.push_back("world");
    }
    std::cout << get_greet(names) << std::endl;
    return 0;
}
