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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** The running server that implements intellij-ext that the IDE is connected to. */
public class IntelliJExtServer {

  private final StreamProcessor stderr;
  private final StreamProcessor stdout;
  private final Process process;
  private final String socket;

  public static IntelliJExtServer create(Path binary, List<String> args) throws IOException {
    String socket = createSocket();
    ProcessBuilder pb = new ProcessBuilder();
    List<String> command = new ArrayList<>();
    command.add(binary.toString());
    command.add("--socket");
    command.add(socket);
    command.addAll(args);
    Process process = pb.command(command).start();
    return new IntelliJExtServer(socket, process);
  }

  /**
   * Creates a directory only readable to the current user that can hold the socket file created by
   * the server. The socket path cannot be more than 140 characters in length, so we try to use /tmp
   * directly as all other directories (IJ settings, user temp, etc) are longer than 140.
   */
  private static String createSocket() throws IOException {
    Set<PosixFilePermission> perm = PosixFilePermissions.fromString("rwx------");
    FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perm);
    String directory = "/tmp";
    Path dir = Files.createTempDirectory(Paths.get(directory), ".idex", attr);
    return dir.resolve(".idx.socket").toString();
  }

  public IntelliJExtServer(String socket, Process process) {
    this.socket = socket;
    this.process = process;
    stderr = new StreamProcessor(process.getErrorStream());
    stdout = new StreamProcessor(process.getInputStream(), "[intellij-ext] ready");
    System.out.printf("Server running [pid %d], waiting for it to startup...%n", process.pid());
  }

  public boolean waitToBeReady() throws InterruptedException {
    return stdout.waitForFirstLine();
  }

  public boolean isAlive() {
    return process.isAlive();
  }

  public void destroy() {
    process.destroyForcibly();
  }

  public String getSocket() {
    return socket;
  }

  private static class StreamProcessor extends Thread {

    private final InputStream stream;
    private final String first;
    private final CountDownLatch latch;

    private volatile boolean foundFirst;

    public StreamProcessor(InputStream stream) {
      this(stream, null);
    }

    public StreamProcessor(InputStream stream, String first) {
      setDaemon(true);
      this.stream = stream;
      this.first = first;
      this.latch = new CountDownLatch(first == null ? 0 : 1);
      this.foundFirst = false;
      this.start();
    }

    @Override
    public void run() {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
        String line = reader.readLine();
        while (line != null) {
          System.out.println(line);
          if (line.equals(first)) {
            foundFirst = true;
            latch.countDown();
          }
          line = reader.readLine();
        }
      } catch (IOException e) {
        // Most likely the process ended. End of process handled elsewhere.
        System.out.println("E");
      }
      latch.countDown();
    }

    public boolean waitForFirstLine() throws InterruptedException {
      latch.await();
      return foundFirst;
    }
  }
}
