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

  import ops._

  implicit val urlBinder = new NodeBinder[Rdf, URL] {

    def fromNode(node: Rdf#Node): Validation[BananaException, URL] =
      asTypedLiteral(node) flatMap {
        case TypedLiteral(lexicalForm, datatype) =>
          if (datatype == anyURI)
            try {
              Success(URL(lexicalForm))
            } catch {
              case t => Failure(FailedConversion(node.toString + " is of type xsd:anyURI but its lexicalForm could not be made a URL: " + lexicalForm))
            }
          else
            Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
      }

    def toNode(t: URL): Rdf#Node = TypedLiteral(t.toString, anyURI)

  }

  // TODO decide if it's a uri or a datatype (if the latter, use a real datatype)
  implicit val assertionSeverityBinder = new NodeBinder[Rdf, AssertionSeverity] {

    def fromNode(node: Rdf#Node): Validation[BananaException, AssertionSeverity] =
      asTypedLiteral(node) flatMap {
        case TypedLiteral(lexicalForm, datatype) =>
          if (datatype == xsd.string)
            try {
              Success(AssertionSeverity(lexicalForm))
            } catch {
              case t => Failure(FailedConversion(node.toString + " is of type xsd:string but its lexicalForm could not be made a AssertionSeverity: " + lexicalForm))
            }
          else
            Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
      }

    def toNode(t: AssertionSeverity): Rdf#Node = t match {
      case Error => "error"
      case Warning => "warning"
      case Info => "info"
    }

  }


  implicit val httpActionBinder = new NodeBinder[Rdf, HttpAction] {

    def fromNode(node: Rdf#Node): Validation[BananaException, HttpAction] = {
      asTypedLiteral(node) flatMap {
        case TypedLiteral(lexicalForm, datatype) =>
          if (datatype == xsd.string)
            try {
              Success(HttpAction(lexicalForm))
            } catch {
              case t => Failure(FailedConversion(node.toString + " is of type xsd:string but its lexicalForm could not be made a HttpAction: " + lexicalForm))
            }
          else
            Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
      }
    }

    def toNode(t: HttpAction): Rdf#Node = t.toString

  }




  implicit val explorationModeBinder = new NodeBinder[Rdf, ExplorationMode] {

    def fromNode(node: Rdf#Node): Validation[BananaException, ExplorationMode] = {
      asTypedLiteral(node) flatMap {
        case TypedLiteral(lexicalForm, datatype) =>
          if (datatype == xsd.string)
            try {
              Success(ExplorationMode(lexicalForm))
            } catch {
              case t => Failure(FailedConversion(node.toString + " is of type xsd:string but its lexicalForm could not be made a ExplorationMode: " + lexicalForm))
            }
          else
            Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
      }
    }

    def toNode(t: ExplorationMode): Rdf#Node = t.toString

  }


}
