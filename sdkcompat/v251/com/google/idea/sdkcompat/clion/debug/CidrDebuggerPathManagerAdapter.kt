package com.google.idea.sdkcompat.clion.debug

import com.jetbrains.cidr.execution.debugger.CidrDebuggerPathManager
import java.io.File

// #api252
object CidrDebuggerPathManagerAdapter {

  fun getBundledGDBBinary(): File {
      return CidrDebuggerPathManager.getBundledGDBBinary()
  }
}