package main.lib

class Greeting {
  companion object {
    private fun getGreeting(name: String): String {
      return "Hello $name!"
    }

    fun getGreetings(names: Array<String>): Array<String> {
      return Array(names.size) { i -> getGreeting(names.get(i)) }
    }
  }
}
