package org.w3

import org.w3.banana._
import org.w3.banana.jena._

package object vs  {

  type Rdf = Jena

//  implicit val ops: RDFOps[Rdf] = JenaOperations

  implicit val diesel: Diesel[Rdf] = JenaDiesel

  implicit val sparql: SparqlOps[Rdf] = JenaSparqlOps

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
