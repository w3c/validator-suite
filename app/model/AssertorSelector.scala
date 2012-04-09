package org.w3.vs.model

import org.w3.vs.assertor._
import org.w3.util.Headers._
import scalaz.Scalaz._

trait AssertorSelector extends (ResourceInfo => Iterable[AssertorId])

object AssertorSelector {

  val noAssertor: AssertorSelector = new AssertorSelector {
    def apply(resourceInfo: ResourceInfo): Iterable[AssertorId] = Seq.empty
  }

  def fromMimeType(pf: PartialFunction[String, Iterable[AssertorId]]) = new AssertorSelector {
    def apply(resourceInfo: ResourceInfo): Iterable[AssertorId] = {
      resourceInfo match {
        case ResourceInfo(id, url, runId, action, timestamp, distanceFromSeed, FetchResult(status, headers, _)) if action === GET => headers.mimetype.collect(pf).flatten
        case _ => Seq.empty
      }
    }
  }

  val simple: AssertorSelector = fromMimeType {
    case "text/html" | "application/xhtml+xml" => Seq(ValidatorNu.id, HTMLValidator.id, I18nChecker.id)
    case "text/css" => Seq(CSSValidator.id)
  }

}

trait AssertorMap extends PartialFunction[ResourceInfo, Iterable[AssertorId]]
