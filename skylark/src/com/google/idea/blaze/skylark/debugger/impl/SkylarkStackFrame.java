/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos;
import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import javax.annotation.Nullable;
import javax.swing.Icon;

class SkylarkStackFrame extends XStackFrame {

  private static final Object STACK_FRAME_EQUALITY_OBJECT = new Object();

  final long threadId;
  private final SkylarkDebuggingProtos.Frame frame;
  private final SkylarkDebugProcess debugProcess;

  SkylarkStackFrame(
      SkylarkDebugProcess debugProcess, long threadId, SkylarkDebuggingProtos.Frame frame) {
    this.debugProcess = debugProcess;
    this.threadId = threadId;
    this.frame = frame;
  }

  @Override
  public Object getEqualityObject() {
    return STACK_FRAME_EQUALITY_OBJECT;
  }

  @Override
  public XDebuggerEvaluator getEvaluator() {
    return new DebuggerEvaluator(debugProcess);
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    return SourcePositionConverter.fromLocationProto(frame.getLocation());
  }

  @Override
  public void computeChildren(XCompositeNode node) {
    if (node.isObsolete() || frame.getScopeCount() == 0) {
      return;
    }
    // show the scopes in reverse order (global first), with only the local scope auto-expanded
    XValueChildrenList children = new XValueChildrenList(frame.getScopeCount());
    for (int i = frame.getScopeCount() - 1; i >= 0; i--) {
      children.addTopGroup(new SkylarkFrameScope(frame.getScope(i), i == 0));
    }
    node.addChildren(children, true);
  }

  /** A group of variables in the debugger tree representing a single scope of a frame. */
  private static class SkylarkFrameScope extends XValueGroup {

    private final boolean autoExpand;
    private final SkylarkDebuggingProtos.Scope scope;

    SkylarkFrameScope(SkylarkDebuggingProtos.Scope scope, boolean autoExpand) {
      super(scope.getName());
      this.scope = scope;
      this.autoExpand = autoExpand;
    }

    @Override
    public boolean isAutoExpand() {
      return autoExpand;
    }

    @Override
    public boolean isRestoreExpansion() {
      return true;
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.Frame;
    }

    @Override
    public String getSeparator() {
      return "";
    }

    @Override
    public void computeChildren(XCompositeNode node) {
      XValueChildrenList children = new XValueChildrenList(scope.getBindingCount());
      scope.getBindingList().forEach(v -> children.add(SkylarkDebugValue.fromProto(v)));
      node.addChildren(children, true);
    }
  }
}
