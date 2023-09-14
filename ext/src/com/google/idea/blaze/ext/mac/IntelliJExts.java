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

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

/**
 * Static utility methods relating to {@link IntelliJExtClient} and {@link IntelliJExtTestServer}.
 * Provides platform-specific implementations to get ManagedChannel using the netty library for Mac.
 */
public final class IntelliJExts {
  public static EventLoopGroup createGroup(DefaultThreadFactory threadFactory) {
    return new KQueueEventLoopGroup(threadFactory);
  }

  public static Class<? extends Channel> getServerChannelType() {
    return KQueueDomainSocketChannel.class;
  }

  public static Class<? extends ServerChannel> getServerChannelType() {
    return KQueueServerDomainSocketChannel.class;
  }
}
