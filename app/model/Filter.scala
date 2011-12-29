package org.w3.vs.model

import org.w3.vs._

import scala.util.matching.Regex

/** A filter for URLs, to be applied to a [[org.w3.vs.model.EntryPointStrategy]]
  * 
  * The semantics of filter is to considerer the `include` first, then the `exclude`
  *
  * @param include
  * @param exclude
  */
case class Filter(include: Include, exclude: Exclude)

/** An `Include` for a [[org.w3.vs.model.Filter]]
  * 
  * It's either `Everything` or a sequence of regexes to be included.
  */
sealed trait Include
case object Everything extends Include
case class IncludeRegex(regs: Seq[Regex]) extends Include

/** An `Exclude` for a [[org.w3.vs.model.Filter]]
  * 
  * It's either `Nothing` or a sequence of regexes to be excluded.
  */
sealed trait Exclude
case object Nothing extends Exclude
case class ExcludeRegex(regs: Seq[Regex]) extends Exclude
