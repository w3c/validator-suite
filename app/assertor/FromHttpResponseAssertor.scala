package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._

trait FromHttpResponseAssertor extends FromURLAssertor {
  
  def assert(context: (OrganizationId, JobId, RunId), response: HttpResponse): AssertorResponse = 
    try {
      val assertions = assert(response.url)
          .groupBy{case a => a.url.toString + a.title}
          .map(_._2)
          .map {case assertions =>
            val contexts = assertions.foldLeft(Iterable[Context]()){case (contexts, a) => contexts ++ a.contexts}
            assertions.head.copy(contexts = contexts.toList)
          }
      AssertorResult(context = context, assertor = name, sourceUrl = response.url, assertions = assertions.toList)
    } catch { case t =>
      AssertorFailure(context = context, assertor = name, sourceUrl = response.url, why = t.getMessage)
    }

}
