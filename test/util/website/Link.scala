package org.w3.vs.util.website

import scala.util.matching.Regex

/** A declarative link between any page whose URL matches `from`
 *  
 *  @param from a regex for the URL being matched
 *  @param pf a partial function passed to the [[scala.util.matching.Regex]]'s extractor (aka. pattern matching).
 *         See [[http://ikaisays.com/2009/04/04/using-pattern-matching-with-regular-expressions-in-scala/ Using pattern matching with regular expressions in Scala]].
 */
case class Link(from: String, pf: PartialFunction[Any, Seq[String]]) {
  
  /** the regex should match the entire URL */
  lazy val regex = new Regex("^"+from+"$")
  
  /** computes out links for the given url
   *  if the regex is matches, the extracted groups are passed to the partial function
   *  if the url is not matched, an empty sequence is returned 
   */
  def outlinksFor(url:String):Seq[String] =
    url match {
      case regex(s) => pf(s)
      case regex(s1, s2) => pf(s1, s2)
      case regex(s1, s2, s3) => pf(s1, s2, s3)
      case regex(s1, s2, s3, s4) => pf(s1, s2, s3, s4)
      case _ => Seq[String]()
    }
  
}

object Link {
  
  /** formats a sequence of URL Strings to a list of links in a <ul> */
  def toUL(links:Iterable[String]):String =
    links map { link => """<li><a href="%s">%s</a></li>""" format (link, link) } mkString ("<ul>\n", "\n", "\n</ul>")
  
}