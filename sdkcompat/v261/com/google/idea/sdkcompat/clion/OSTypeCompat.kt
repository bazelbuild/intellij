package com.google.idea.sdkcompat.clion

import com.intellij.util.system.OS

// #api253
object OSTypeCompat {

  @JvmStatic
  fun getCurrent(): OS {
    return OS.CURRENT
  }
}
