package org.w3.vs.model

/** An event coming from an observation
 * 
 *  @param severity a severity (either: error, warning, info)
 *  @param id identifier that defines uniquely this kind of event
 *  @param lang XML lang code
 *  @param contexts a sequence of [[org.w3.vs.validator.Context]]s
 */
case class RawAssertion(severity: String, assertId: String, lang: String, contexts: Seq[Context], title: String, description: Option[String]) {
  def isError = severity == "error"
  def isWarning = severity == "warning"
  def isInfo = severity == "info"
  val id = java.util.UUID.randomUUID.toString.substring(0,6)
}
