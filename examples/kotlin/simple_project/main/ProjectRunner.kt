package main

import main.lib.Greeting

class ProjectRunner {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      var names: Array<String> = arrayOf("World")

      if (args.size > 0) {
        names = args
      }

      val greeting = Greeting.getGreetings(names)
      println(greeting.contentToString())
    }
  }
}
