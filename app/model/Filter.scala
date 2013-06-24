package org.w3.vs.model

import org.w3.vs.web.URL
import scala.util.matching.Regex

object Filter {

  def prefixRegex(prefix: String): Regex = new Regex("^" + prefix + ".*$")

  def includePrefix(prefix: String): Filter = includePrefixes(prefix)

  def includePrefixes(prefix: String, prefixes: String*): Filter =
    Filter(IncludeRegex((prefix +: prefixes.toSeq) map prefixRegex), Nothing)

  val includeEverything: Filter = Filter(include = Everything, exclude = Nothing)

}

/** A filter for URLs, to be applied to a [[org.w3.vs.model.Strategy]]
  * 
  * The semantics of filter is to considerer the `include` first, then the `exclude`
  *
  * @param include
  * @param exclude
  */
case class Filter(include: Include, exclude: Exclude) {

  def passThrough(url: URL): Boolean = include.accepts(url) && ! exclude.rejects(url)

}

/** An `Include` for a [[org.w3.vs.model.Filter]]
  * 
  * It's either `Everything` or a sequence of regexes to be included.
  */
sealed trait Include {
  def accepts(url: URL): Boolean = this match {
    case Everything => true
    case IncludeRegex(regs) => regs exists { reg => reg.findFirstIn(url.toString).isDefined }
  }
}

case object Everything extends Include
case class IncludeRegex(regs: Seq[Regex]) extends Include

/** An `Exclude` for a [[org.w3.vs.model.Filter]]
  * 
  * It's either `Nothing` or a sequence of regexes to be excluded.
  */
sealed trait Exclude {
  def rejects(url: URL): Boolean = this match {
    case Nothing => false
    case ExcludeRegex(regs) => regs exists { reg => reg.findFirstIn(url.toString).isDefined }
  }
}

case object Nothing extends Exclude
case class ExcludeRegex(regs: Seq[Regex]) extends Exclude
