package org.w3.vs.assertor

import org.w3.vs.util._
import org.w3.vs.model._

trait FromHttpResponseAssertor extends FromURLAssertor {
 
  import Assertor.logger

  def supportedMimeTypes: List[String]

  /** this function is the occasion to fix some problems coming from the
    * assertors. We do that here because this property is not enforced
    * at the Unicorn level.
    * 
    * we group the assertions per url, then by title, then we merge
    * the assertions sharing the same contexts into one single
    * assertion. One can then assume that all returned Assertion-s
    * will have a different AssertionTypeId.
    */
  def assert(response: HttpResponse, configuration: AssertorConfiguration): AssertorResponse = {
    val start = System.currentTimeMillis()
    val result = try {
      val assertions =
        assert(response.url, configuration).groupBy(_.url).mapValues[Vector[Assertion]] { assertionsCommonURL =>
          var assertionsWithGroupedTitles = Vector.empty[Assertion]
          assertionsCommonURL.groupBy(_.title).values.foreach { assertionsCommonTitle =>
            var groupedContexts = Vector.empty[Context]
            assertionsCommonTitle foreach { groupedContexts ++= _.contexts }
            val newAssertion = assertionsCommonTitle.head.copy(contexts = groupedContexts)
            assertionsWithGroupedTitles :+= newAssertion
          }
          assertionsWithGroupedTitles
        }
      val end = System.currentTimeMillis()
      logger.info(s"id=${id} status=completed time=${end - start}ms url=${response.url}")
      AssertorResult(assertor = id, sourceUrl = response.url, assertions = assertions)
    } catch { case t: Throwable =>
      val end = System.currentTimeMillis()
      logger.error(s"""id=${id} status=failed time=${end - start}ms url=${response.url} message="${t.getMessage}" """)
      AssertorFailure(assertor = id, sourceUrl = response.url, why = t.getMessage)
    }
    
    result
  }

}
