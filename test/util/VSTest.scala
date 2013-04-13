package org.w3.vs.util

import org.w3.vs._
import org.scalatest._
import akka.testkit.{TestKit, ImplicitSender}
import org.w3.vs.model._
import play.api.{Mode, Configuration}
import org.w3.vs.util.html.Doctype
import org.w3.vs.store.MongoStore

object VSTest {

  def newTestConfiguration() = {
    new ValidatorSuite(mode = Mode.Test)
      with DefaultActorSystem
      with DefaultDatabase
      with DefaultHttpClient
      with DefaultRunEvents
  }

}

trait DefaultTest extends Suite with BeforeAndAfterAll with MustMatchers with WordSpec with Inside

trait VSTest[A <: ValidatorSuite]
  extends DefaultTest { this: Suite with BeforeAndAfterAll =>

  implicit val vs: A

  override def afterAll() {
    vs.shutdown()
    super.afterAll()
  }

}

abstract class VSTestKit[A <: ValidatorSuite with ActorSystem](val vs: A)
  extends TestKit(vs.system)
    with TestKitHelper
    with ImplicitSender
    with DefaultTest { this: DefaultTest with TestKit with ImplicitSender =>

  implicit val _vs = vs

  override def afterAll() {
    vs.shutdown()
    super.afterAll()
  }

}

/*abstract class SystemTest(val vs: ActorSystem = new ValidatorSuite(mode = Mode.Test) with DefaultActorSystem)
    extends TestKit(vs.system)
      with VSTest
      with TestKitHelper
      with ImplicitSender {
  implicit val konf = vs
}

abstract class DBTest(
  vs: ActorSystem with Database = new ValidatorSuite(mode = Mode.Test) with DefaultActorSystem with DefaultDatabase)
    extends SystemTest(vs){
}

abstract class HttpClientTest(
  vs: ActorSystem with HttpClient = new ValidatorSuite(mode = Mode.Test) with DefaultActorSystem with DefaultHttpClient)
    extends SystemTest(vs){
}

abstract class RunEventsTest(
  override val vs: ActorSystem with HttpClient with Database with RunEvents =
    new ValidatorSuite(mode = Mode.Test)
      with DefaultActorSystem
      with DefaultHttpClient
      with DefaultDatabase
      with DefaultRunEvents
  ) extends SystemTest(vs) {
  override implicit val konf = vs
  val http = vs.httpActorRef
  val runEventBus = vs.runEventBus
}*/


