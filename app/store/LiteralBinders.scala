package org.w3.vs.store

import org.w3.vs.model._
import org.w3.banana._
import org.w3.banana.NodeBinder._
import scalaz._
import scalaz.Scalaz._
import scalaz.Validation._
import org.w3.util._

trait LiteralBinders[Rdf <: RDF] {
this: Binders[Rdf] =>

  import diesel._
  import ops._

  implicit val urlBinder: TypedLiteralBinder[Rdf, URL] = new TypedLiteralBinder[Rdf, URL] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Validation[BananaException, URL] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == anyURI)
        try {
          Success(URL(lexicalForm))
        } catch {
          case t => Failure(FailedConversion(literal.toString + " is of type xsd:anyURI but its lexicalForm could not be made a URL: " + lexicalForm))
        }
      else
        Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
    }

    def toTypedLiteral(t: URL): Rdf#TypedLiteral = TypedLiteral(t.toString, anyURI)

  }

  // TODO decide if it's a uri or a datatype (if the latter, use a real datatype)
  implicit val assertionSeverityBinder: TypedLiteralBinder[Rdf, AssertionSeverity] = new TypedLiteralBinder[Rdf, AssertionSeverity] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Validation[BananaException, AssertionSeverity] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == xsd.string)
        try {
          Success(AssertionSeverity(lexicalForm))
        } catch {
          case t => Failure(FailedConversion(literal.toString + " is of type xsd:string but its lexicalForm could not be made a AssertionSeverity: " + lexicalForm))
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
      StringBinder.toTypedLiteral(literal)
    }

  }


  implicit val httpActionBinder: TypedLiteralBinder[Rdf, HttpAction] = new TypedLiteralBinder[Rdf, HttpAction] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Validation[BananaException, HttpAction] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == xsd.string)
        try {
          Success(HttpAction(lexicalForm))
        } catch {
          case t => Failure(FailedConversion(literal.toString + " is of type xsd:string but its lexicalForm could not be made a HttpAction: " + lexicalForm))
        }
      else
        Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
    }

    def toTypedLiteral(t: HttpAction): Rdf#TypedLiteral = StringBinder.toTypedLiteral(t.toString)

  }




  implicit val explorationModeBinder: TypedLiteralBinder[Rdf, ExplorationMode] = new TypedLiteralBinder[Rdf, ExplorationMode] {

    def fromTypedLiteral(literal: Rdf#TypedLiteral): Validation[BananaException, ExplorationMode] = {
      val TypedLiteral(lexicalForm, datatype) = literal
      if (datatype == xsd.string)
        try {
          Success(ExplorationMode(lexicalForm))
        } catch {
          case t => Failure(FailedConversion(literal.toString + " is of type xsd:string but its lexicalForm could not be made a ExplorationMode: " + lexicalForm))
        }
      else
        Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
    }

    def toTypedLiteral(t: ExplorationMode): Rdf#TypedLiteral = StringBinder.toTypedLiteral(t.toString)

  }


}
