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

import com.google.idea.blaze.ext.BlueprintServiceGrpc.BlueprintServiceBlockingStub;
import com.google.idea.blaze.ext.BuildCleanerServiceGrpc.BuildCleanerServiceFutureStub;
import com.google.idea.blaze.ext.BuildServiceGrpc.BuildServiceBlockingStub;
import com.google.idea.blaze.ext.BuildServiceGrpc.BuildServiceFutureStub;
import com.google.idea.blaze.ext.ChatBotModelGrpc.ChatBotModelBlockingStub;
import com.google.idea.blaze.ext.CitcOperationsServiceGrpc.CitcOperationsServiceBlockingStub;
import com.google.idea.blaze.ext.CodeSearchGrpc.CodeSearchFutureStub;
import com.google.idea.blaze.ext.CritiqueServiceGrpc.CritiqueServiceBlockingStub;
import com.google.idea.blaze.ext.DepServerGrpc.DepServerFutureStub;
import com.google.idea.blaze.ext.ECatcherServiceGrpc.ECatcherServiceFutureStub;
import com.google.idea.blaze.ext.ExperimentsServiceGrpc.ExperimentsServiceBlockingStub;
import com.google.idea.blaze.ext.FileApiGrpc.FileApiFutureStub;
import com.google.idea.blaze.ext.FindingsServiceGrpc.FindingsServiceBlockingStub;
import com.google.idea.blaze.ext.IntelliJExtGrpc.IntelliJExtBlockingStub;
import com.google.idea.blaze.ext.IssueTrackerGrpc.IssueTrackerBlockingStub;
import com.google.idea.blaze.ext.KytheGrpc.KytheFutureStub;
import com.google.idea.blaze.ext.LinterGrpc.LinterFutureStub;
import com.google.idea.blaze.ext.PiperServiceGrpc.PiperServiceBlockingStub;
import com.google.idea.blaze.ext.PiperServiceGrpc.PiperServiceFutureStub;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** The local client connected to the running intellij-ext service. */
public class IntelliJExtClient {

  private final IntelliJExtBlockingStub stub;
  private final EventLoopGroup eventLoopGroup;
  private final ManagedChannel channel;

  public static IntelliJExtClient create(Path socket) {
    EventLoopGroup eventLoopGroup = createEventLoopGroup();
    ManagedChannel channel =
        NettyChannelBuilder.forAddress(new DomainSocketAddress(socket.toFile()))
            .eventLoopGroup(eventLoopGroup)
            .channelType(IntelliJExts.getClientChannelType())
            .maxInboundMessageSize(1024 * 1024 * 1024) // To avoid RESOURCE_EXHAUSED errors
            .withOption(ChannelOption.SO_KEEPALIVE, false)
            .usePlaintext()
            .build();
    return new IntelliJExtClient(channel, eventLoopGroup);
  }

  /**
   * Provides a way to register test channel for intellij ext test cases. It's used to avoid
   * connecting to the real intellij ext server but use the mock one that unit test created.
   */
  public static IntelliJExtClient createForTest(ManagedChannel channel) {
    EventLoopGroup eventLoopGroup = createEventLoopGroup();
    return new IntelliJExtClient(channel, eventLoopGroup);
  }

  private IntelliJExtClient(ManagedChannel channel, EventLoopGroup eventLoopGroup) {
    this.channel = channel;
    this.eventLoopGroup = eventLoopGroup;
    stub = IntelliJExtGrpc.newBlockingStub(channel);
  }

  /** Gracefully shutdown the underlying channel. */
  public void shutdown() {
    channel.shutdown();
    eventLoopGroup.shutdownGracefully().awaitUninterruptibly(30, TimeUnit.SECONDS); // Best effort.
  }

  /** Forcibly shutdown the underlying channel. */
  public void shutdownNow() {
    channel.shutdownNow();
    Future<?> unused = eventLoopGroup.shutdownGracefully(); // Best effort.
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

  public FindingsServiceBlockingStub getFindingsService() {
    return FindingsServiceGrpc.newBlockingStub(channel);
  }

  public CritiqueServiceBlockingStub getCritiqueService() {
    return CritiqueServiceGrpc.newBlockingStub(channel);
  }

  public BuildCleanerServiceFutureStub getBuildCleanerService() {
    return BuildCleanerServiceGrpc.newFutureStub(channel);
  }

  public DepServerFutureStub getDependencyService() {
    return DepServerGrpc.newFutureStub(channel);
  }

  public CitcOperationsServiceBlockingStub getCitcOperationsService() {
    return CitcOperationsServiceGrpc.newBlockingStub(channel);
  }

  public CodeSearchFutureStub getCodeSearchService() {
    return CodeSearchGrpc.newFutureStub(channel);
  }

  public ECatcherServiceFutureStub getECatcherService() {
    return ECatcherServiceGrpc.newFutureStub(channel);
  }

  public PiperServiceFutureStub getPiperService() {
    return PiperServiceGrpc.newFutureStub(channel);
  }

  public PiperServiceBlockingStub getPiperServiceBlocking() {
    return PiperServiceGrpc.newBlockingStub(channel);
  }

  public BlueprintServiceBlockingStub getBlueprintService() {
    return BlueprintServiceGrpc.newBlockingStub(channel);
  }

  private static EventLoopGroup createEventLoopGroup() {
    return IntelliJExts.createGroup(new DefaultThreadFactory(EventLoopGroup.class, true));
  }
}
