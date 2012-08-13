package org.w3

import org.w3.banana._
import org.w3.banana.jena._

package object vs  {

  type Rdf = Jena

  implicit val diesel: Diesel[Rdf] = JenaDiesel

  implicit val sparql: SPARQLOperations[Rdf] = JenaSPARQLOperations

}
