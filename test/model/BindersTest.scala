package org.w3.vs.model

import org.scalatest._
import org.scalatest.matchers._
import scalaz._
import org.w3.banana._
import org.w3.banana.jena._
import org.joda.time.{ DateTime, DateTimeZone }

class BindersTest extends WordSpec with MustMatchers {

  val binders = Binders(JenaOperations, JenaGraphUnion, JenaGraphTraversal)
  import binders._

  def testSerializeDeserialize[T](binder: PointedGraphBinder[Jena, T])(t: T) = {
    import binder._
    fromPointedGraph(toPointedGraph(t)) must be === (Success(t))
  }

  "OrganizationVO" in {
    testSerializeDeserialize(OrganizationVOBinder) {
      OrganizationVO(name = "foo", admin = UserId())
    }
  }

  "JobVO" in {
    testSerializeDeserialize(JobVOBinder) {
      JobVO(
        name = "foo",
        createdOn = DateTime.now(DateTimeZone.UTC),
        creatorId = UserId(),
        organizationId = OrganizationId(),
        strategyId = StrategyId())
    }
  }

}
