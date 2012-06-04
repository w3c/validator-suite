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
      assertions => AssertorResultClosed(AssertorResult(
          jobId = response.jobId,
          runId = response.runId,
          assertorId = id,
          sourceUrl = response.url,
          errors = assertions.count(_.assertion.severity == Error),
          warnings = assertions.count(_.assertion.severity == Warning)),
          assertions)
    )
}