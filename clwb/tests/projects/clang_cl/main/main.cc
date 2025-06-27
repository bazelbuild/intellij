#include <iostream>

int main(int argc, char** argv) {
#ifdef __llvm__
    printf("Hello LLVM\n");
#else
    printf("Hello MSVC\n");
#endif
}
