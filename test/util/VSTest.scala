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

abstract class VSTest extends WordSpec with BeforeAndAfterAll with Matchers with Inside {

  implicit def vs: ValidatorSuite

  override def beforeAll() {
    super.beforeAll()
    vs.start()
    vs.db
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


trait VSTestKit
    extends VSTest
    with TestKitBase
    with TestKitHelper {

  implicit lazy val vs: ValidatorSuite = new ValidatorSuite { val mode = Mode.Test }
  implicit lazy val system : akka.actor.ActorSystem = vs.system
  implicit def self = testActor

}
