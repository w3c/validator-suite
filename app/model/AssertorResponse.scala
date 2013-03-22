package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import scalaz._
import scalaz.Scalaz._

sealed trait AssertorResponse {
  val assertor: AssertorId
  val sourceUrl: URL
}
case class AssertorFailure(
    assertor: AssertorId,
    sourceUrl: URL,
    why: String) extends AssertorResponse

case class AssertorResult(
    assertor: AssertorId,
    sourceUrl: URL,
    /** assumption: the assertions are grouped by URL */
    assertions: Map[URL, Vector[Assertion]]) extends AssertorResponse

//case class GroupedAssertions(grouped: Map[URL, Vector[Assertion]]) extends AnyVal {
//
//  def toIterable: Iterable[(URL, Vector[Assertion])] = grouped
//
//}
