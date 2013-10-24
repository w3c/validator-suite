package org.w3.vs.assertor

import org.w3.vs.util._
import org.w3.vs.web._
import org.w3.vs.model._
import scala.io.Source
import play.api.Configuration
import java.util.concurrent.{ Executors, ForkJoinPool }
import java.io.File
import com.ning.http.client.{AsyncHttpClientConfig, AsyncHttpClient}

object FromURLAssertor {

  // any reason for not sharing the configuration with application.http-client?
  private val client: AsyncHttpClient = {
    val configuration = Configuration.load(new File("."))
    val timeout =
      configuration.getInt("application.assertor.http-client.timeout") getOrElse sys.error("application.assertor.http-client.timeout")
    val userAgent =
      configuration.getString("application.assertor.http-client.user-agent") getOrElse sys.error("application.assertor.http-client.user-agent")
//    val executor = new ForkJoinPool()
    val builder = new AsyncHttpClientConfig.Builder()
    val config =
      builder
//        .setExecutorService(executor)
        .setFollowRedirects(true)
        .setUserAgent(userAgent)
        .setConnectionTimeoutInMs(timeout)
        .build()
    new AsyncHttpClient(config)
  }

}

/**
 * An assertor that returns assertions about a document pointed by a URL
 */
trait FromURLAssertor extends FromSourceAssertor {
  
  import FromURLAssertor.client

  /**
   * returns the URL to be used by a machine to validate
   * the given URL against this assertor
   */
  def validatorURLForMachine(url: URL, configuration: AssertorConfiguration): URL
  
  /**
   * returns the URL to be used by a human to validate
   * the given URL against this assertor
   * The default is validatorURLForMachine and you can override it
   */
  def validatorURLForHuman(url: URL, configuration: AssertorConfiguration): URL = validatorURLForMachine(url, configuration)
  
  /** 
   *  @param url a pointer to the document
   *  @return the assertion
   */
  def assert(url: URL, configuration: AssertorConfiguration): Iterable[Assertion] = {
    Assertor.logger.info(s"id=${id} status=started url=${url}")
    val inputStream =
      client.prepareGet(validatorURLForMachine(url, configuration).toString).execute().get().getResponseBodyAsStream()
    val source = Source.fromInputStream(inputStream)
    assert(source)
  }
  
}
