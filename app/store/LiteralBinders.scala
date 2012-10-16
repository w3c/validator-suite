package org.w3.vs.store

import org.w3.vs.model._
import org.w3.banana._
import org.w3.util._
import org.w3.vs._
import diesel._
import ops._
import scala.util._

trait LiteralBinders {
self: Binders =>

  implicit val assertorIdBinder: TypedLiteralBinder[Rdf, AssertorId] = new TypedLiteralBinder[Rdf, AssertorId] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Try[AssertorId] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == xsd.string)
        try {
          Success(AssertorId(lexicalForm))
        } catch {
          case _: Exception => Failure(FailedConversion(literal.toString() + " is of type xsd:string but its lexicalForm could not be made a AssertorId: " + lexicalForm))
        }
      else
        Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
    }

    def toTypedLiteral(t: AssertorId): Rdf#TypedLiteral = StringLiteralBinder.toTypedLiteral(t.id)

  }

  implicit val urlBinder: TypedLiteralBinder[Rdf, URL] = new TypedLiteralBinder[Rdf, URL] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Try[URL] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == anyURI)
        try {
          Success(URL(lexicalForm))
        } catch {
          case _: Exception => Failure(FailedConversion(literal.toString() + " is of type xsd:anyURI but its lexicalForm could not be made a URL: " + lexicalForm))
        }
      else
        Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
    }

    def toTypedLiteral(t: URL): Rdf#TypedLiteral = TypedLiteral(t.toString, anyURI)

  }

  // TODO decide if it's a uri or a datatype (if the latter, use a real datatype)
  implicit val assertionSeverityBinder: TypedLiteralBinder[Rdf, AssertionSeverity] = new TypedLiteralBinder[Rdf, AssertionSeverity] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Try[AssertionSeverity] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == xsd.string)
        try {
          Success(AssertionSeverity(lexicalForm))
        } catch {
          case _: Exception => Failure(FailedConversion(literal.toString() + " is of type xsd:string but its lexicalForm could not be made a AssertionSeverity: " + lexicalForm))
        }
      else
        Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
    }

    def toTypedLiteral(t: AssertionSeverity): Rdf#TypedLiteral = {
      val literal = t match {
        case Error => "error"
        case Warning => "warning"
        case Info => "info"
      }
      StringLiteralBinder.toTypedLiteral(literal)
    }

  }


  implicit val httpMethodBinder: TypedLiteralBinder[Rdf, HttpMethod] = new TypedLiteralBinder[Rdf, HttpMethod] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Try[HttpMethod] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == xsd.string)
        try {
          Success(HttpMethod(lexicalForm))
        } catch {
          case _: Exception => Failure(FailedConversion(literal.toString() + " is of type xsd:string but its lexicalForm could not be made a HttpMethod: " + lexicalForm))
        }
      else
        Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
    }

    def toTypedLiteral(t: HttpMethod): Rdf#TypedLiteral = StringLiteralBinder.toTypedLiteral(t.toString)

  }




  implicit val explorationModeBinder: TypedLiteralBinder[Rdf, ExplorationMode] = new TypedLiteralBinder[Rdf, ExplorationMode] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Try[ExplorationMode] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == xsd.string)
        try {
          Success(ExplorationMode(lexicalForm))
        } catch {
          case _: Exception => Failure(FailedConversion(literal.toString() + " is of type xsd:string but its lexicalForm could not be made a ExplorationMode: " + lexicalForm))
        }
      else
        Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
    }

    def toTypedLiteral(t: ExplorationMode): Rdf#TypedLiteral = StringLiteralBinder.toTypedLiteral(t.toString)

  }


}
