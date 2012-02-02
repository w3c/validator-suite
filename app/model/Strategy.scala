package org.w3.vs.model

import org.w3.util._
import java.util.UUID

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
  def shouldObserve(url: URL): Boolean
  def fetch(url: URL, distance: Int): HttpAction
  
}

