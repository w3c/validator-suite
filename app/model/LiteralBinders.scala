package org.w3.vs.model

import org.w3.banana._
import org.w3.banana.diesel._
import scalaz._
import scalaz.Scalaz._
import scalaz.Validation._
import org.w3.util._

trait LiteralBinders[Rdf <: RDF] {

  val ops: RDFOperations[Rdf]

  import ops._

  private val xsd = XSDPrefix(ops)
  private val anyURI = xsd("anyURI")

  implicit val urlBinder = new LiteralBinder[Rdf, URL] {

    def fromLiteral(literal: Rdf#Literal): Validation[BananaException, URL] = {
      Literal.fold(literal)(
        {
          case TypedLiteral(lexicalForm, datatype) =>
            if (datatype == anyURI)
              try {
                Success(URL(lexicalForm))
              } catch {
                case t => Failure(FailedConversion(literal.toString + " is of type xsd:anyURI but its lexicalForm could not be made a URL: " + lexicalForm))
              }
            else
              Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
        },
        langLiteral => Failure(FailedConversion(langLiteral + " is a langLiteral, you want to access its lexical form"))
      )
    }

    def toLiteral(t: URL): Rdf#Literal = TypedLiteral(t.toString, anyURI)

  }

  // TODO decide if it's a uri or a datatype (if the latter, use a real datatype)
  implicit val assertionSeverityBinder = new LiteralBinder[Rdf, AssertionSeverity] {

    def fromLiteral(literal: Rdf#Literal): Validation[BananaException, AssertionSeverity] = {
      Literal.fold(literal)(
        {
          case TypedLiteral(lexicalForm, datatype) =>
            if (datatype == xsd.string)
              try {
                Success(AssertionSeverity(lexicalForm))
              } catch {
                case t => Failure(FailedConversion(literal.toString + " is of type xsd:string but its lexicalForm could not be made a AssertionSeverity: " + lexicalForm))
              }
            else
              Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
        },
        langLiteral => Failure(FailedConversion(langLiteral + " is a langLiteral, you want to access its lexical form"))
      )
    }

    def toLiteral(t: AssertionSeverity): Rdf#Literal = t match {
      case Error => "error"
      case Warning => "warning"
      case Info => "info"
    }

  }


  implicit val httpActionBinder = new LiteralBinder[Rdf, HttpAction] {

    def fromLiteral(literal: Rdf#Literal): Validation[BananaException, HttpAction] = {
      Literal.fold(literal)(
        {
          case TypedLiteral(lexicalForm, datatype) =>
            if (datatype == xsd.string)
              try {
                Success(HttpAction(lexicalForm))
              } catch {
                case t => Failure(FailedConversion(literal.toString + " is of type xsd:string but its lexicalForm could not be made a HttpAction: " + lexicalForm))
              }
            else
              Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
        },
        langLiteral => Failure(FailedConversion(langLiteral + " is a langLiteral, you want to access its lexical form"))
      )
    }

    def toLiteral(t: HttpAction): Rdf#Literal = t.toString

  }



  implicit val headersBinder = new LiteralBinder[Rdf, Headers] {

    import java.util.{Map => jMap, List => jList, HashMap => jHashMap, LinkedList}
    import scala.collection.JavaConverters._
    val regex = """^([^:]+):\s*(.*)$""".r

    def fromLiteral(literal: Rdf#Literal): Validation[BananaException, Headers] = {
      Literal.fold(literal)(
        {
          case TypedLiteral(lexicalForm, datatype) =>
            if (datatype == xsd.string)
              try {
                Success {
                  val lines = lexicalForm.split("\n").toIterator
                  val map: Map[String, List[String]] = lines.map {
                    case regex("null", statusline) => (null, List(statusline))
                    case regex(key, values) => (key, values.split(",").toList)
                  }.toMap
                  map
                }
              } catch {
                case t => Failure(FailedConversion(literal.toString + " is of type xsd:string but its lexicalForm could not be made Headers: " + lexicalForm))
              }
            else
              Failure(FailedConversion(lexicalForm + " has datatype " + datatype))
        },
        langLiteral => Failure(FailedConversion(langLiteral + " is a langLiteral, you want to access its lexical form"))
      )
    }

    def toLiteral(t: Headers): Rdf#Literal =
      t map { case (k, v) => "%s: %s" format (k, v.mkString(",")) } mkString "\n"

  }


}
