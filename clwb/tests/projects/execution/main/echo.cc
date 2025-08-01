#include <cstdio>
#include <cerrno>
#include <climits>
#include <unistd.h>

int main(const int argc, const char **argv) {
    FILE* f = fopen("output.txt", "w+");
    if (f == NULL) {
        return errno;
    }

    for (int i = 1; i < argc; i++) {
        fprintf(f, "%s\n", argv[i]);
    }

    if (fclose(f) != 0) {
        return errno;
    }

    char cwd[PATH_MAX];
    if (getcwd(cwd, sizeof(cwd)) == NULL) {
        return errno;
    }

    fprintf(stdout, "ECHO_OUTPUT_FILE: %s/output.txt\n", cwd);

    return 0;
}
