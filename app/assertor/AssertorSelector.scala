package org.w3.vs.assertor

import org.w3.util.Headers._
import org.w3.vs.model._
import scalaz.Scalaz._

trait AssertorSelector extends (ResourceResponse => Iterable[Assertor]) {
  def name: String

  override def equals(other: Any): Boolean = other match {
    case ass: AssertorSelector => this.name == ass.name
    case _ => false
  }

  override def toString = name

}

object AssertorSelector {

  val noAssertor: AssertorSelector = new AssertorSelector {
    val name = "no-assertor"
    def apply(response: ResourceResponse): Iterable[Assertor] = Seq.empty
  }

  def fromMimeType(pf: PartialFunction[String, Iterable[Assertor]]) = new AssertorSelector {
    val name = "from-mime-type"
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

