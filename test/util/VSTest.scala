package org.w3.vs.util

import org.w3.vs._
import org.scalatest._
import akka.testkit.{TestKit, ImplicitSender}
import org.w3.vs.store.MongoStore
import play.api.Mode.Test

object VSTest {

  def newTestConfiguration() = new ValidatorSuite {
    val mode = Test
  }

}

trait VSTest extends Suite with BeforeAndAfterAll with MustMatchers with WordSpec with Inside {

  implicit def vs: ValidatorSuite

  override def beforeAll() {
    super.beforeAll()
    vs.start()
  }

  override def afterAll() {
    vs.shutdown()
    super.afterAll()
  }

}

trait WipeoutData extends VSTest {

  override def beforeAll() {
    super.beforeAll()
    MongoStore.reInitializeDb()(vs)
  }

}


abstract class VSTestKit(val vs: ValidatorSuite)
  extends TestKit(vs.system)
    with VSTest
    with TestKitHelper
    with ImplicitSender {

  implicit val _vs = vs

}
