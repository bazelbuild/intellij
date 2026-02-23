#include <cstdio>

int main(int argc, const char* argv[]) {
#ifdef FOO
    printf("FOO");
#endif
#ifdef BAR
    printf("BAR");
#endif
}
