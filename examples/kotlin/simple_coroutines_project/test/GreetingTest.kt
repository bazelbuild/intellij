package test

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runBlockingTest
import main.lib.Greeting
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class GreetingTest {
  @Test
  fun testGetGreeting() = runBlockingTest {
    val startTime = currentTime
    val greeting = Greeting.getGreeting(8)
    val totalTime = currentTime - startTime
    assertThat(totalTime).isEqualTo(8000)
    assertThat(greeting).isEqualTo("Hello from Coroutine 8")
  }
}
