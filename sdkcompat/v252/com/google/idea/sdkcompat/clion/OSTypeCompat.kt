package com.google.idea.sdkcompat.clion

import com.jetbrains.cidr.toolchains.OSType

// #api253
object OSTypeCompat {

  @JvmStatic
  fun getCurrent(): OSType {
    return OSType.getCurrent()
  }
}
