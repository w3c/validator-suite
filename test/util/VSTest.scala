package org.w3.vs.util

import org.w3.vs._
import org.scalatest._
import akka.testkit.{TestKitBase, TestKit, ImplicitSender}
import org.w3.vs.store.MongoStore
import play.api.Mode.Test
import play.api.Mode

object VSTest {

  def newTestConfiguration() = new ValidatorSuite {
    val mode = Test
  }

}

trait VSTest extends WordSpecLike with BeforeAndAfterAll with Matchers {

  implicit def vs: ValidatorSuite

  override def beforeAll() {
    vs.start()
    vs.db
  }

  override def afterAll() {
    vs.shutdown()
  }

}

trait WipeoutData extends VSTest {

  override def beforeAll() {
    super.beforeAll()
    import org.w3.vs.util.timer._
    MongoStore.reInitializeDb()(vs).getOrFail()
  }

}


class VSTestKit(val conf: ValidatorSuite = VSTest.newTestConfiguration())
    extends TestKit(conf.system)
    with VSTest
    with TestKitHelper {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  implicit val vs: ValidatorSuite = conf
  implicit def self = testActor

}
