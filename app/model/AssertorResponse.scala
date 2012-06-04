package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation

sealed trait AssertorResponse {
  val id: AssertorResponseId
  val jobId: JobId
  val runId: RunId
  val assertorId: AssertorId
  val sourceUrl: URL
  val timestamp: DateTime
}

// not a persisted object for now
case class AssertorFailure(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = new DateTime,
    why: String) extends AssertorResponse

/** 
 *  AssertorResult 
 */
// closed with number of errors and warnings
case class AssertorResult(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = DateTime.now,
    errors: Int,
    warnings: Int) extends AssertorResponse {
  
  //def isValid = ! hasError
  //def hasError: Boolean = assertions exists {_.severity == Error}
  //def hasWarnings: Boolean = assertions exists {_.severity == Warning}
  //def numberOfErrors = assertions.filter(_.severity == Error).size
  //def numberOfWarnings = assertions.filter(_.severity == Warning).size
  
  //def getJob: FutureVal[Exception, Job] = sys.error("ni")
  //def getRun: FutureVal[Exception, Run] = sys.error("ni")
  //def getAssertor: FutureVal[Exception, Assertor] = sys.error("ni")
  //def getAssertions: FutureVal[Exception, Iterable[Assertion]] = Assertion.getForResponse(id)
  //def save(): FutureVal[Exception, AssertorResult] = AssertorResult.save(this)
}

case class AssertorResultClosed(assertorResult: AssertorResult, assertionsClosed: Iterable[AssertionClosed])

object AssertorResult {
  
  //def get(id: AssertorResponseId)(implicit conf: VSConfiguration): FutureVal[Exception, AssertorResult] = sys.error("ni")
  //def getForJob(id: JobId, after: Option[DateTime] = None)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[AssertorResult]] = sys.error("ni")
  //def getForURL(url: URL)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[AssertorResult]] = sys.error("ni")
  //def save(result: AssertorResult)(implicit conf: VSConfiguration): FutureVal[Exception, AssertorResult] = {
  //  implicit def ec = conf.webExecutionContext
  //  FutureVal.successful(result)
  //}
}