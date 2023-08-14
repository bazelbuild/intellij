/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.ext;

import com.google.idea.blaze.ext.IntelliJExtGrpc.IntelliJExtImplBase;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;

public final class IntelliJExtTestServer extends IntelliJExtImplBase {

  @Override
  public void getVersion(GetVersionRequest request, StreamObserver<Version> responseObserver) {
    Version v = Version.newBuilder().setDescription("test server").setVersion("1.0").build();
    responseObserver.onNext(v);
    responseObserver.onCompleted();
  }

  @Override
  public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
    responseObserver.onNext(PingResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getStatus(GetStatusRequest request, StreamObserver<Status> responseObserver) {
    String pid = String.valueOf(ProcessHandle.current().pid());

    Status status =
        Status.newBuilder()
            .addStatus(StatusValue.newBuilder().setProperty("pid").setValue(pid))
            .build();
    responseObserver.onNext(status);
    responseObserver.onCompleted();
  }

  public static void main(String[] args) throws Exception {

    String socket = null;
    boolean failToInitialize = false;
    Iterator<String> it = Arrays.stream(args).iterator();
    while (it.hasNext()) {
      String arg = it.next();
      if (arg.equals("--socket") && it.hasNext()) {
        socket = it.next();
      } else if (arg.equals("--fail_to_initialize")) {
        failToInitialize = true;
      }
    }

    Path path = Paths.get(socket);
    EpollEventLoopGroup group =
        new EpollEventLoopGroup(new DefaultThreadFactory(EventLoopGroup.class, true));
    NettyServerBuilder sb =
        NettyServerBuilder.forAddress(new DomainSocketAddress(path.toString()))
            .channelType(EpollServerDomainSocketChannel.class)
            .bossEventLoopGroup(group)
            .addService(new IntelliJExtTestServer())
            .workerEventLoopGroup(group);

    Server server = sb.build().start();
    if (failToInitialize) {
      System.exit(1);
    }
    System.out.println("[intellij-ext] ready");
    server.awaitTermination();
  }
}
