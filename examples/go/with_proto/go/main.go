package main

import (
	"fmt"
	"github.com/bazelbuild/intellij/examples/go/with_proto/go/lib"
	"github.com/bazelbuild/intellij/examples/go/with_proto/proto"
	"google.golang.org/grpc"
)

func main() {
	num := 3
	fmt.Printf("AddToTwo(%s) = %s", num, lib.AddToTwo(num))
}

func serv(grpcSrv *grpc.Server, protoSrv *proto.FooServiceServer) {
	name := "World"
	req := proto.HelloRequest{Name: &name}
	req.GetName()
}
