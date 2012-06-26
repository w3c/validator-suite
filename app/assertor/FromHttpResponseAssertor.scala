package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._

trait FromHttpResponseAssertor extends FromURLAssertor {
  
  def assert(response: HttpResponse): FutureVal[AssertorFailure, Iterable[AssertorResult]] = 
    assert(response.url, response.jobId, response.runId) fold (
      throwable => AssertorFailure(
          jobId = response.jobId,
          runId = response.runId,
          assertorId = id,
          sourceUrl = response.url,
          why = throwable),
      assertions => {
        val groupedAssertions: Iterable[(URL, Iterable[AssertionClosed])] = 
          assertions.groupBy(_.assertion.url).map{ case (url, assertions1) => {
            val flattenByTitle = assertions1.groupBy(_.assertion.title).map{ case (title, assertions2) => {
              val assertion = assertions2.head.assertion
              val contexts = assertions2.foldLeft[Iterable[Context]](Iterable()){ (contexts, assertionC) => {
                val ctxs = assertionC.contexts map (_.copy(assertionId = assertion.id))
                contexts ++ ctxs
              }}
              AssertionClosed(assertion, contexts)
            }}
            (url, flattenByTitle)
          }}
        groupedAssertions.map{case (url, assertions) =>
          AssertorResult(
            jobId = response.jobId,
            runId = response.runId,
            assertorId = id,
            sourceUrl = response.url,
            url = url,
            assertions = assertions)
        }
      }
    )
}