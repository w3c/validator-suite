package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scalaz._
import Validation._
import scala.io.Source
import akka.dispatch.ExecutionContext

trait FromHttpResponseAssertor extends FromURLAssertor {
  
  def assert(response: HttpResponse): FutureVal[AssertorFailure, AssertorResultClosed] = 
    assert(response.url, response.jobId, response.runId) fold (
      throwable => AssertorFailure(
          jobId = response.jobId,
          runId = response.runId,
          assertorId = id,
          sourceUrl = response.url,
          why = throwable.getMessage),
      assertions => {
        val groupedAssertions: Iterable[AssertionClosed] = assertions.groupBy(_.assertion.title.trim).map{
          case (title, assertionsClosed) => {
            val assertion = assertionsClosed.head.assertion
            val contexts = assertionsClosed.foldLeft[Iterable[Context]](Iterable()){ (contexts, assertionClosed) => {
                val ctxs = assertionClosed.contexts map (_.copy(assertionId = assertion.id))
                contexts ++ ctxs
              }}
            AssertionClosed(assertion, contexts)
          }
        }
        AssertorResultClosed(AssertorResult(
          jobId = response.jobId,
          runId = response.runId,
          assertorId = id,
          sourceUrl = response.url,
          // TODO
          errors = groupedAssertions.count(_.assertion.severity == Error),
          warnings = groupedAssertions.count(_.assertion.severity == Warning)),
          groupedAssertions)
      }
    )
}