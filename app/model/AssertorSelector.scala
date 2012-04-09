package org.w3.vs.model

import org.w3.vs.assertor._
import org.w3.util.Headers._
import scalaz.Scalaz._

trait AssertorSelector extends (ResourceInfo => Iterable[FromURLAssertor])

object AssertorSelector {

  val noAssertor: AssertorSelector = new AssertorSelector {
    def apply(resourceInfo: ResourceInfo): Iterable[FromURLAssertor] = Seq.empty
  }

  def fromMimeType(pf: PartialFunction[String, Iterable[FromURLAssertor]]) = new AssertorSelector {
    def apply(resourceInfo: ResourceInfo): Iterable[FromURLAssertor] = {
      resourceInfo match {
        case ResourceInfo(id, url, runId, action, timestamp, distanceFromSeed, FetchResult(status, headers, _)) if action === GET => headers.mimetype.collect(pf).flatten
        case _ => Seq.empty
      }
    }
  }

  val simple: AssertorSelector = fromMimeType {
    case "text/html" | "application/xhtml+xml" => Seq(ValidatorNu, HTMLValidator, I18nChecker)
    case "text/css" => Seq(CSSValidator)
  }

}

trait AssertorMap extends PartialFunction[ResourceInfo, Iterable[FromURLAssertor]]
