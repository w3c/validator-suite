package org.w3.vs.model

import org.w3.util._
import java.util.UUID
import org.w3.vs.assertor._
import org.w3.util._
import org.w3.util.Headers._
import scalaz.Scalaz._

/** An strategy to be used by the Run
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

  def maxNumberOfResources: Int

  def fetch(url: URL, distance: Int): HttpAction

  def assertorsFor: AssertorSelector
  
}

