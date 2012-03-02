package org.w3.vs.model

/** An event coming from an observation
 * 
 *  @param severity a severity (either: error, warning, info)
 *  @param id identifier that defines uniquely this kind of event
 *  @param lang XML lang code
 *  @param contexts a sequence of [[org.w3.vs.validator.Context]]s
 */
case class RawAssertion(severity: String, id: String, lang: String, contexts: Seq[Context]) {
  def isError = severity == "error"
  def isWarning = severity == "warning"
}
