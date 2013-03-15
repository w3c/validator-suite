package org.w3.vs.actor

import org.w3.vs.model._
import scala.math.Ordering

object Classifier {

  val ordering: Ordering[Classifier] = ???

  case object AllRunEvents extends Classifier
  case object AllRunDatas extends Classifier
  case object AllResourceDatas extends Classifier
  case object AllAssertions extends Classifier
  case object AllGroupedAssertionDatas extends Classifier

}

trait Classifier {

  import Classifier._

  def matches(event: Any): Boolean = this match {
    case AllRunEvents => event.isInstanceOf[RunEvent]
    case AllRunDatas => event.isInstanceOf[RunData]
    case AllResourceDatas => event.isInstanceOf[ResourceData]
    case AllAssertions => event.isInstanceOf[Assertion]
    case AllGroupedAssertionDatas => event.isInstanceOf[GroupedAssertionData]
  }

}
