#include <cstdio>

int main(int argc, const char* argv[]) {
#if defined(FOO)
    printf("FOO\n");
#elif defined(BAR)
    printf("BAR\n");
#else
    printf("OTHER\n");
#endif
}
