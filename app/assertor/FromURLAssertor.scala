package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source

/**
 * An assertor that returns assertions about a document pointed by a URL
 */
trait FromURLAssertor extends FromSourceAssertor {

  /**
   * utility method to encode a URL
   */
  def encodedURL(url: URL): String =
    java.net.URLEncoder.encode(url.toString, "UTF-8")
  
  /**
   * returns the URL to be used by a machine to validate
   * the given URL against this assertor
   */
  def validatorURLForMachine(url: URL): URL
  
  /**
   * returns the URL to be used by a human to validate
   * the given URL against this assertor
   * The default is validatorURLForMachine and you can override it
   */
  def validatorURLForHuman(url: URL): URL = validatorURLForMachine(url)
  
  /** 
   *  @param url a pointer to the document
   *  @return the assertion
   */
  def assert(url: URL, jobId: JobId, runId: RunId): FutureVal[Throwable, Iterable[AssertionClosed]] = FutureVal {
    Source.fromURL(validatorURLForMachine(url))
  } flatMap { source => 
    assert(source, jobId, runId)
  }
  
}

/*trait URLToSourceAssertor extends FromURLAssertor with FromSourceAssertor {
  
  // TODO
  import org.w3.vs.Prod.configuration
  implicit def ec = configuration.webExecutionContext
  
  override def assert(url: URL): FutureVal[Throwable, Iterable[AssertionClosed]] = FutureVal {
    Source.fromURL(validatorURLForMachine(url))
  } flatMap { source => 
    assert(source)
  }
  
}*/


