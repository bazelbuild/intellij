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

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.ext.BuildServiceGrpc.BuildServiceBlockingStub;
import com.google.idea.blaze.ext.BuildServiceGrpc.BuildServiceFutureStub;
import com.google.idea.blaze.ext.ChatBotModelGrpc.ChatBotModelBlockingStub;
import com.google.idea.blaze.ext.ExperimentsServiceGrpc.ExperimentsServiceBlockingStub;
import com.google.idea.blaze.ext.IntelliJExtGrpc.IntelliJExtBlockingStub;
import com.google.idea.blaze.ext.IssueTrackerGrpc.IssueTrackerBlockingStub;
import com.google.idea.blaze.ext.KytheGrpc.KytheFutureStub;
import com.google.idea.blaze.ext.LinterGrpc.LinterFutureStub;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A service that maintains the connection to an external extension service. The external service is
 * passed in as a grpc server binary. This class ensures there is an active connection to the
 * server, and restarts the server and client if such a connection is lost.
 */
public final class IntelliJExtService {

  private final Path binary;
  private final List<String> serverArgs;

  private IntelliJExtServer server;
  private IntelliJExtClient client;
  private volatile ServiceStatus status;

  private static final Logger logger = Logger.getLogger(IntelliJExtService.class.getName());

  /** The status of the connection to the server */
  public enum ServiceStatus {
    INITIALIZING,
    READY,
    FAILED,
  }

  public IntelliJExtService(Path binary) {
    this.binary = binary;
    this.serverArgs = new ArrayList<>();
    this.status = ServiceStatus.INITIALIZING;
  }

  private synchronized void start() throws IOException {
    status = ServiceStatus.INITIALIZING;
    if (server != null) {
      // This would be a rare situation where the server process still exists
      // but we are in the situation that we couldn't connect to it. This
      // would mean that the process is alive and the grpc server is down or
      // not responsive, we kill it before starting a new one.
      server.destroy();
    }
    try {
      server = IntelliJExtServer.create(binary, serverArgs);
      client = IntelliJExtClient.create(server.getSocket());
      boolean ready = server.waitToBeReady();
      status = ready ? ServiceStatus.READY : ServiceStatus.FAILED;
    } catch (IOException e) {
      status = ServiceStatus.FAILED;
      throw e;
    }
  }

  /**
   * A lazy connection to the server. If there is no client the server is started, and a client
   * connected to it. Every time this is called a ping is issued to the server to ensure it's
   * running, if it has died for some reason, the server is restarted and a new client is connected
   * to it.
   *
   * <p>Note this does not implement a retry loop intentionally. There should not be a case where we
   * "try again", just to see if next time works. This connection should be stable. There are
   * legitimate reasons why the server is gone, crashed or simply killed itself after a period of
   * inactivity. This method will start it up again, and resume execution.
   */
  private synchronized IntelliJExtBlockingStub connect() throws IOException {
    if (client == null) {
      start();
    }
    try {
      return client.getStubSafe();
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "Error running the intellij-ext server, restarting", e);
      start();
      return client.getStubSafe();
    }
  }

  public ServiceStatus getServiceStatus() {
    return status;
  }

  @VisibleForTesting
  public void additionalServerArguments(String s) {
    serverArgs.add(s);
  }

  // Simple implementations of the stub methods
  public String getVersion() throws IOException {
    IntelliJExtBlockingStub stub = connect();
    Version version = stub.getVersion(GetVersionRequest.getDefaultInstance());
    return version.getDescription() + "\n" + version.getVersion();
  }

  /**
   * A set of property, value pairs that describe the current status of the server, property names
   * are descriptive only and not to be depended on.
   */
  public Map<String, String> getStatus() throws IOException {
    IntelliJExtBlockingStub stub = connect();
    Status response = stub.getStatus(GetStatusRequest.getDefaultInstance());
    HashMap<String, String> status = new HashMap<>();
    for (StatusValue s : response.getStatusList()) {
      status.put(s.getProperty(), s.getValue());
    }
    return status;
  }

  public IssueTrackerBlockingStub getIssueTrackerService() throws IOException {
    IntelliJExtBlockingStub unused = connect();
    return client.getIssueTrackerService();
  }

  public BuildServiceFutureStub getBuildService() throws IOException {
    IntelliJExtBlockingStub unused = connect();
    return client.getBuildService();
  }

  public BuildServiceBlockingStub getBuildServiceBlocking() throws IOException {
    IntelliJExtBlockingStub unused = connect();
    return client.getBuildServiceBlocking();
  }

  public KytheFutureStub getKytheService() {
    try {
      IntelliJExtBlockingStub unused = connect();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return client.getKytheService();
  }

  public ExperimentsServiceBlockingStub getExperimentsService() throws IOException {
    IntelliJExtBlockingStub unused = connect();
    return client.getExperimentsService();
  }

  public ChatBotModelBlockingStub getChatBotModelService() throws IOException {
    IntelliJExtBlockingStub unused = connect();
    return client.getChatBotModelService();
  }

  public LinterFutureStub getLinterService() throws IOException {
    IntelliJExtBlockingStub unused = connect();
    return client.getLinterService();
  }
}
