package akka.sample.osgi.test

import scala.concurrent.duration._
import akka.actor._
import akka.sample.osgi.api.{ DiningHakkersService, Identify, Identification }
import akka.sample.osgi.test.TestOptions._
import org.junit.runner.RunWith
import org.junit.{ Before, Test }
import org.ops4j.pax.exam.{ Option ⇒ PaxOption }
import org.ops4j.pax.exam.junit.{ Configuration, JUnit4TestRunner }
import org.ops4j.pax.exam.util.Filter
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit
import javax.inject.Inject
import java.util.concurrent.{ TimeUnit, SynchronousQueue }
import akka.testkit.TestProbe
import scala.Some

/**
 * This is a ScalaTest based integration test. Pax-Exam, which is responsible for loading the test class into
 * the OSGi environment and executing it, currently does not support ScalaTest directly. However, ScalaTest
 * provides a JUnit-compatible runner, so the test is defined to use that runner. Pax-Exam can then invoke
 * it as a normal JUnit test. Because Pax Exam is using the JUnitRunner and not one of the ScalaTest traits such
 * as FunSuite, the test must be defined using the JUnit @Test annotation.
 *
 * This is a simple test demonstrating in-container integration testing.
 *
 * One thing to note is that we only depend on the API bundle, not the implementation in core. The implementation
 * is injected into the test at runtime via an OSGi service lookup performed by Pax Exam.
 *
 * TODO attempt to use the Akka test probe
 */
@RunWith(classOf[JUnit4TestRunner])
class HakkerStatusTest extends JUnitSuite with ShouldMatchersForJUnit {

  @Inject @Filter(timeout = 30000)
  var actorSystem: ActorSystem = _

  @Inject @Filter(timeout = 30000)
  var service: DiningHakkersService = _

  var testProbe: TestProbe = _

  @Configuration
  def config: Array[PaxOption] = Array[PaxOption](
    karafOptionsWithTestBundles(),
    featureDiningHakkers() //,debugOptions()
    )

  // Junit @Before and @After can be used as well

  @Before
  def setupAkkaTestkit() {
    testProbe = new TestProbe(actorSystem)
  }

  @Test
  def verifyObtainingAHakkerViaTheTheDiningHakkersService() {

    val name = "TestHakker"
    val hakker = Some(service.getHakker(name, (math.floor(math.random * 5)).toInt))
      .getOrElse(throw new IllegalStateException("No Hakker was created via DiningHakkerService"))

    hakker should not be (null)

    // takes some time for the first message to get through
    testProbe.within(5.seconds) {
      testProbe.send(hakker, Identify)
      val Identification(fromHakker, busyWith) = testProbe.expectMsgType[Identification]

      println("---------------> %s is busy with %s.".format(fromHakker, busyWith))
      fromHakker should be("TestHakker")
      busyWith should not be (null)
    }

  }

}

object HakkerStatusTest {
  class Interrogator(queue: SynchronousQueue[(String, String)]) extends Actor {
    def receive = {
      case msg: Identification ⇒ {
        queue.put((msg.name, msg.busyWith))
      }
    }
  }
}
