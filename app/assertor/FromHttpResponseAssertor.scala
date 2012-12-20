package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._

trait FromHttpResponseAssertor extends FromURLAssertor {
 
  import Assertor.logger

  def supportedMimeTypes: List[String]

  def assert(context: Run.Context, response: HttpResponse, configuration: AssertorConfiguration): AssertorResponse = {
    val start = System.currentTimeMillis()
    val result = try {
      val assertions = assert(response.url, configuration)
          .groupBy{case a => a.url.toString + a.title}
          .map(_._2)
          .map {case assertions =>
            val contexts = assertions.foldLeft(Iterable[Context]()){case (contexts, a) => contexts ++ a.contexts}
            assertions.head.copy(contexts = contexts.toList)
          }
      AssertorResult(context = context, assertor = id, sourceUrl = response.url, assertions = assertions.toList)
    } catch { case t: Throwable =>
      AssertorFailure(context = context, assertor = id, sourceUrl = response.url, why = t.getMessage)
    }
    val end = System.currentTimeMillis()
    logger.debug("%s took %dms to assert %s" format (this.name, end - start, response.url))
    result
  }

}
