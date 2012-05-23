package org.w3.vs.assertor

import org.w3.util.Headers._
import org.w3.vs.model._
import scalaz.Scalaz._

trait AssertorSelector extends (ResourceResponse => Iterable[Assertor])

object AssertorSelector {

  val noAssertor: AssertorSelector = new AssertorSelector {
    def apply(response: ResourceResponse): Iterable[Assertor] = Seq.empty
  }

  def fromMimeType(pf: PartialFunction[String, Iterable[Assertor]]) = new AssertorSelector {
    def apply(response: ResourceResponse): Iterable[Assertor] = {
      response match {
        case a: HttpResponse if a.action == GET => a.headers.mimetype.collect(pf).flatten
        case _ => Seq.empty
      }
    }
  }

  val simple: AssertorSelector = fromMimeType {
    case "text/html" | "application/xhtml+xml" => Seq(ValidatorNu, HTMLValidator, I18nChecker)
    case "text/css" => Seq(CSSValidator)
  }

}

