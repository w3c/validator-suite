package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._

trait FromHttpResponseAssertor extends FromURLAssertor {
  
  def assert(context: (OrganizationId, JobId, RunId), response: HttpResponse): FutureVal[AssertorFailure, AssertorResult] = 
    FutureVal {
      val assertions = assert(response.url)
      AssertorResult(context = context, assertorId = id, sourceUrl = response.url, assertions = assertions)
    } failMap { throwable =>
      AssertorFailure(context = context, assertorId = id, sourceUrl = response.url, why = throwable)
    }

}
