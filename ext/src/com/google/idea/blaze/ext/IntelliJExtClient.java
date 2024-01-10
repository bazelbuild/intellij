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

import com.google.idea.blaze.ext.BuildCleanerServiceGrpc.BuildCleanerServiceFutureStub;
import com.google.idea.blaze.ext.BuildServiceGrpc.BuildServiceBlockingStub;
import com.google.idea.blaze.ext.BuildServiceGrpc.BuildServiceFutureStub;
import com.google.idea.blaze.ext.ChatBotModelGrpc.ChatBotModelBlockingStub;
import com.google.idea.blaze.ext.CodeSearchGrpc.CodeSearchFutureStub;
import com.google.idea.blaze.ext.DepServerGrpc.DepServerFutureStub;
import com.google.idea.blaze.ext.ExperimentsServiceGrpc.ExperimentsServiceBlockingStub;
import com.google.idea.blaze.ext.FileApiGrpc.FileApiFutureStub;
import com.google.idea.blaze.ext.IntelliJExtGrpc.IntelliJExtBlockingStub;
import com.google.idea.blaze.ext.IssueTrackerGrpc.IssueTrackerBlockingStub;
import com.google.idea.blaze.ext.KytheGrpc.KytheFutureStub;
import com.google.idea.blaze.ext.LinterGrpc.LinterFutureStub;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.nio.file.Path;

/** The local client connected to the running intellij-ext service. */
public class IntelliJExtClient {

  private final IntelliJExtBlockingStub stub;
  private final ManagedChannel channel;

  public IntelliJExtClient(Path socket) {
    this(
        NettyChannelBuilder.forAddress(new DomainSocketAddress(socket.toFile()))
            .eventLoopGroup(
                IntelliJExts.createGroup(new DefaultThreadFactory(EventLoopGroup.class, true)))
            .channelType(IntelliJExts.getClientChannelType())
            .withOption(ChannelOption.SO_KEEPALIVE, false)
            .usePlaintext()
            .build());
  }

  public IntelliJExtClient(ManagedChannel channel) {
    this.channel = channel;
    stub = IntelliJExtGrpc.newBlockingStub(channel);
  }

  public static IntelliJExtClient create(Path socket) {
    return new IntelliJExtClient(socket);
  }

  /**
   * Provides a way to register test channel for intellij ext test cases. It's used to avoid
   * connecting to the real intellij ext server but use the mock one that unit test created.
   */
  public static IntelliJExtClient createForTest(ManagedChannel channel) {
    return new IntelliJExtClient(channel);
  }

  /**
   * Gets the client stub after performing a ping to the server. If it returns a stub, it means
   * there is an active connection to the server. If not an exception is thrown.
   */
  public IntelliJExtBlockingStub getStubSafe() {
    PingResponse unused = stub.ping(PingRequest.getDefaultInstance());
    return stub;
  }

  public IssueTrackerBlockingStub getIssueTrackerService() {
    return IssueTrackerGrpc.newBlockingStub(channel);
  }

  public BuildServiceFutureStub getBuildService() {
    return BuildServiceGrpc.newFutureStub(channel);
  }

  public BuildServiceBlockingStub getBuildServiceBlocking() {
    return BuildServiceGrpc.newBlockingStub(channel);
  }

  public KytheFutureStub getKytheService() {
    return KytheGrpc.newFutureStub(channel);
  }

  public ExperimentsServiceBlockingStub getExperimentsService() {
    return ExperimentsServiceGrpc.newBlockingStub(channel);
  }

  public ChatBotModelBlockingStub getChatBotModelService() {
    return ChatBotModelGrpc.newBlockingStub(channel);
  }

  public LinterFutureStub getLinterService() {
    return LinterGrpc.newFutureStub(channel);
  }

  public FileApiFutureStub getFileApiService() {
    return FileApiGrpc.newFutureStub(channel);
  }

  public BuildCleanerServiceFutureStub getBuildCleanerService() {
    return BuildCleanerServiceGrpc.newFutureStub(channel);
  }

  public DepServerFutureStub getDependencyService() {
    return DepServerGrpc.newFutureStub(channel);
  }

  public CodeSearchFutureStub getCodeSearchService() {
    return CodeSearchGrpc.newFutureStub(channel);
  }
}
