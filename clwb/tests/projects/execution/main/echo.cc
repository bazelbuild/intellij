#include <iostream>
#include <fstream>
#include <filesystem>

int main(int argc, char* argv[]) {
   std::ofstream outputFile("output.txt");
   if (!outputFile) {
       return 1;
   }

   for (int i = 1; i < argc; ++i) {
       outputFile << argv[i] << std::endl;
   }

    auto fullPath = std::filesystem::current_path() / "output.txt";
    std::cout << "ECHO_OUTPUT_FILE: " << fullPath.string() << std::endl;

    return 0;
}
