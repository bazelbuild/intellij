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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/** The running server that implements intellij-ext that the IDE is connected to. */
public class IntelliJExtServer {

  private final StreamProcessor stderr;
  private final StreamProcessor stdout;
  private final Process process;
  private final Path socket;

  private static final Logger logger = Logger.getLogger(IntelliJExtServer.class.getName());

  public static IntelliJExtServer create(Path binary, List<String> args) throws IOException {
    Path socket = createSocket();
    ProcessBuilder pb = new ProcessBuilder();
    List<String> command = new ArrayList<>();
    command.add(binary.toString());
    command.add("--socket");
    command.add(socket.toString());
    command.addAll(args);
    Process process = pb.command(command).start();
    return new IntelliJExtServer(socket, process);
  }

  /**
   * Creates a directory only readable to the current user that can hold the socket file created by
   * the server. The socket path cannot be more than 104 characters in length, so we try to use /tmp
   * directly as all other directories (IJ settings, user temp, etc) are longer. (eg.
   * https://man.freebsd.org/cgi/man.cgi?query=unix&sektion=4)
   */
  private static Path createSocket() throws IOException {
    Set<PosixFilePermission> perm = PosixFilePermissions.fromString("rwx------");
    FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perm);
    Path dir = Files.createTempDirectory(Paths.get("/tmp"), ".idex", attr);
    return dir.resolve(".idx.socket");
  }

  public IntelliJExtServer(Path socket, Process process) {
    this.socket = socket;
    this.process = process;
    stderr = new StreamProcessor(process.getErrorStream());
    stdout = new StreamProcessor(process.getInputStream(), "[intellij-ext] ready");
    logger.info(
        String.format("Server running [pid %d], waiting for it to startup...", process.pid()));
  }

  public boolean waitToBeReady() {
    return stdout.waitForFirstLine();
  }

  public void destroy() {
    process.destroyForcibly();
  }

  public Path getSocket() {
    return socket;
  }
}
