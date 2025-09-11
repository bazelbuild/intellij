#include "person.pb.h"
#include "local_proto/animal.pb.h"

int main(int argc, char* argv[]) {
  GOOGLE_PROTOBUF_VERIFY_VERSION;

  external::Person person;
  local::Animal animal;

  return 0;
}
