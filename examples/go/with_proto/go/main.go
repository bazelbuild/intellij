package main

import (
	"fmt"
	"github.com/bazelbuild/intellij/examples/go/with_proto/go/external"
	"github.com/bazelbuild/intellij/examples/go/with_proto/go/lib"
	"github.com/bazelbuild/intellij/examples/go/with_proto/proto"
	"google.golang.org/grpc"
)

func main() {
	num := 3
	fmt.Printf("AddToTwo(%d) = %d", num, lib.AddToTwo(num))
}

// serv exists to check that:
// - Third party symbols (`grpc.Server`) resolve.
// - Code generated from protobuf resolves (symbols in the `proto` package).
//   - We should be able to "go to definition" in `proto.FooServiceServer`.
//   - We should be able to go to definition in `GetName`, a completely generated method.
func serv(grpcSrv *grpc.Server, protoSrv *proto.FooServiceServer) {
	name := "World"
	req := proto.HelloRequest{Name: &name}
	req.GetName()
}

// CountExternalCats function exists to check that packages that are named `external` resolve:
//
//	We should be able to "go to definition" in `external.ExternalCats()`.
func CountExternalCats() int {
	return len(external.ExternalCats())
}
