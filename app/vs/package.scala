package org.w3

import org.w3.banana._
import org.w3.banana.jena._

package object vs  {

  type Rdf = Jena

  implicit val diesel: Diesel[Rdf] = JenaDiesel

  implicit val sparql: SparqlOps[Rdf] = JenaSparqlOps

//  implicit def graphSyntax(graph: Rdf#Graph): syntax.GraphSyntax[Rdf] = new syntax.GraphSyntax[Rdf](graph)
//
//  implicit def nodeSyntax(node: Rdf#Node): syntax.NodeSyntax[Rdf] = new syntax.NodeSyntax[Rdf](node)
//
//  implicit def uriSyntax(uri: Rdf#URI): syntax.URISyntax[Rdf] = new syntax.URISyntax[Rdf](uri)
//
//  implicit def literalSyntax(literal: Rdf#Literal): syntax.LiteralSyntax[Rdf] = new syntax.LiteralSyntax[Rdf](literal)
//
//  implicit def typedLiteralSyntax(tl: Rdf#TypedLiteral): syntax.TypedLiteralSyntax[Rdf] = new syntax.TypedLiteralSyntax[Rdf](tl)
//
//  implicit def langLiteralSyntax(ll: Rdf#LangLiteral): syntax.LangLiteralSyntax[Rdf] = new syntax.LangLiteralSyntax[Rdf](ll)
//
//  implicit def stringSyntax(s: String): syntax.StringSyntax = new syntax.StringSyntax(s)
//
//  implicit def anySyntax[T](t: T): syntax.AnySyntax[T] = new syntax.AnySyntax[T](t)
//
//  implicit def sparqlSolutionSyntax(solution: Rdf#Solution): syntax.SparqlSolutionSyntax[Rdf] = new syntax.SparqlSolutionSyntax[Rdf](solution)
//
//  implicit def sparqlSolutionsSyntax(solutions: Rdf#Solutions): syntax.SparqlSolutionsSyntax[Rdf] = new syntax.SparqlSolutionsSyntax[Rdf](solutions)
//
//  implicit def toPointedGraphW(node: Rdf#Node)(implicit ops: RDFOps[Rdf]): PointedGraphW[Rdf] = new PointedGraphW[Rdf](PointedGraph(node)(ops))
//
}

//import org.w3.banana._
//import org.w3.banana.sesame._
//
//package object vs  {
//
//  type Rdf = Sesame
//
//  implicit val diesel: Diesel[Rdf] = SesameDiesel
//
//  implicit val sparql: SPARQLOperations[Rdf] = SesameSPARQLOperations
//
//}
