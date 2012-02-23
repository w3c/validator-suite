package org.w3.vs.model

import org.w3.util._
import java.util.UUID
import org.w3.vs.assertor._
import org.w3.util._
import org.w3.util.Headers._

/** An strategy to be used by the Observer
  * 
  * @param uuid a unique identifier
  * @param name a name
  * @param delay a personalized delay between GETs (overrides the default one)
  */
trait Strategy {
  val uuid: UUID
  val name: String
  
  def seedURLs: Iterable[URL]
  def mainAuthority: Authority
  // TODO maybe this should move somewhere else
  def assertorsFor(resourceInfo: ResourceInfo): Iterable[FromURLAssertor] = {
    resourceInfo match {
      case ResourceInfo(id, url, runId, action, timestamp, distanceFromSeed, Fetch(status, headers, _)) if fetch(url, distanceFromSeed) == GET =>
        headers.mimetype match {
          case Some("text/html") | Some("application/xhtml+xml") => List(HTMLValidator)
          case Some("text/css") => List(CSSValidator)
          case _ => List.empty
        }
      case _ => List.empty
    }
  }
    
  def fetch(url: URL, distance: Int): HttpAction
  
}

