import org.scalatest._
import flatspec._
import matchers._

class HelloTest extends AnyFlatSpec with should.Matchers {
  "Greeting" should "work" in {
    Hello.greeting should be ("Hello world!")
  }
}