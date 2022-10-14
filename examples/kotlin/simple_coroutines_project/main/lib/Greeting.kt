package main.lib

import kotlinx.coroutines.delay

object Greeting {
  suspend fun getGreeting(id: Int): String {
    delay(1000L * id)
    return "Hello from Coroutine $id"
  }
}
