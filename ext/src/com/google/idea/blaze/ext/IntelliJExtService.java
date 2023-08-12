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

import com.google.idea.blaze.ext.IntelliJExtGrpc.IntelliJExtBlockingStub;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  private synchronized void start() throws IOException, InterruptedException {
    status = ServiceStatus.INITIALIZING;
    if (server != null) {
      server.destroy();
    }
    server = IntelliJExtServer.create(binary, serverArgs);
    client = IntelliJExtClient.create(server.getSocket());
    boolean ready = server.waitToBeReady();
    status = ready ? ServiceStatus.READY : ServiceStatus.FAILED;
  }

  /**
   * A lazy connection to the server. If there is no client the server is started, and a client
   * connected to it. Every time this is called a ping is issued to the server to ensure it's
   * running, if it has died for some reason, the server is restarted and a new client is connected
   * to it.
   */
  private synchronized IntelliJExtBlockingStub connect() throws IOException, InterruptedException {
    if (client == null) {
      start();
    }
    try {
      return client.getStubSafe();
    } catch (Exception e) {
      System.out.println("Something is wrong with the server, restarting");
      start();
      return client.getStubSafe();
    }
  }

  public ServiceStatus getServiceStatus() {
    return status;
  }

  public void additionalServerArguments(String s) {
    serverArgs.add(s);
  }

  // Simple implementations of the stub methods
  public String getVersion() throws IOException, InterruptedException {
    IntelliJExtBlockingStub stub = connect();
    Version version = stub.getVersion(GetVersionRequest.newBuilder().build());
    return version.getDescription() + "\n" + version.getVersion();
  }

  public Map<String, String> getStatus() throws IOException, InterruptedException {
    IntelliJExtBlockingStub stub = connect();
    Status response = stub.getStatus(GetStatusRequest.newBuilder().build());
    HashMap<String, String> status = new HashMap<>();
    for (StatusValue s : response.getStatusList()) {
      status.put(s.getProperty(), s.getValue());
    }
    return status;
  }
}
