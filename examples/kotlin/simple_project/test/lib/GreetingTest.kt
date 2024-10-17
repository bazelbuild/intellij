package test.lib

import com.google.common.truth.Truth.assertThat
import main.lib.Greeting
import org.junit.Test

internal class GreetingTest {

  @Test
  fun testGetNoGreetings() {
    val actual = Greeting.getGreetings(arrayOf())
    assertThat(actual).isEmpty()
  }

  @Test
  fun testGetSingleGreeting() {
    val actual = Greeting.getGreetings(arrayOf("Usersss"))
    assertThat(actual).asList().containsExactly("Hello Usersss!")
  }

  @Test
  fun testGetMultipleGreetings() {
    val actual = Greeting.getGreetings(arrayOf("User 1", "User 2", "User 3"))
    val expected = arrayOf("Hello User 1!", "Hello User 2!", "Hello User 3!")
    assertThat(actual).asList().containsExactlyElementsIn(expected)
  }
}
