package main

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class Main {
  companion object {
    /**
     * Create a list of coroutines (based on the user input). Each coroutine is delayed for
     * a number of minutes relative to its order in the list, i.e. coroutine {@code i} is delayed
     * for {@code i} seconds. After the delay, the coroutine prints a greeting message indicating
     * its number.
     * */
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
      val num: Int

      if (args.size > 0) {
        num = args[0].toInt()
      } else {
        num = 1
      }

      for (i in 0 until num) {
        launch {
          delay(1000L * (i + 1))
          println("Hello from Coroutine $i")
        }
      }

      // this should be printed before any of the coroutines output their messages
      println("Hello from Main coroutine")
    }
  }
}
