package test

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import main.lib.Greeting
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class GreetingTest {
  @Test
  fun testGetGreeting() =
    runTest(UnconfinedTestDispatcher()) {
      val startTime = currentTime
      val greeting = Greeting.getGreeting(7)
      val totalTime = currentTime - startTime
      assertThat(totalTime).isEqualTo(7000)
      assertThat(greeting).isEqualTo("Hello from Coroutine 7")
    }
}
