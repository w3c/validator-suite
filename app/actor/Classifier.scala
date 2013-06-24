package org.w3.vs.actor

import org.w3.vs.model._
import scala.math.Ordering
import org.w3.vs.web.URL

object Classifier {

  implicit val ordering: Ordering[Classifier] = new Ordering[Classifier] {
    def compare(x: Classifier, y: Classifier): Int = (x, y) match {
      case (ResourceDataFor(u1), ResourceDataFor(u2)) => URL.ordering.compare(u1, u2)
      case (AssertionsFor(u1), AssertionsFor(u2)) => URL.ordering.compare(u1, u2)
      case _ => x.hashCode - y.hashCode
    }
  }

  case object AllRunEvents extends Classifier {
    type OneOff = RunEvent
    type Streamed = RunEvent
  }
  case object AllRunDatas extends Classifier {
    type OneOff = RunData
    type Streamed = RunData
  }
  case object AllResourceDatas extends Classifier {
    type OneOff = Iterable[ResourceData]
    type Streamed = ResourceData
  }
  case class ResourceDataFor(url: URL) extends Classifier {
    type OneOff = ResourceData
    type Streamed = ResourceData
  }
  case object AllAssertions extends Classifier
  case class AssertionsFor(url: URL) extends Classifier {
    type OneOff = Seq[Assertion]
    type Streamed = Assertion
  }
  case object AllGroupedAssertionDatas extends Classifier {
    type OneOff = Iterable[GroupedAssertionData]
    type Streamed = GroupedAssertionData
  }

}

sealed trait Classifier {

  /** the type  */
  type OneOff

  /** the type for the elements  */
  type Streamed

  import Classifier._

  def matches(event: Any): Boolean = this match {
    case AllRunEvents => event.isInstanceOf[RunEvent]
    case AllRunDatas => event.isInstanceOf[RunData]
    case AllResourceDatas => event.isInstanceOf[ResourceData]
    case ResourceDataFor(url) => event match {
      case ResourceData(`url`, _, _, _) => true
      case _ => false
    }
    case AllAssertions => event.isInstanceOf[Assertion]
    case AssertionsFor(url) => event match {
      case Assertion(_, `url`, _, _, _, _, _, _, _) => true
      case _ => false
    }
    case AllGroupedAssertionDatas => event.isInstanceOf[GroupedAssertionData]
  }

}
