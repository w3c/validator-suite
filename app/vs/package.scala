package org.w3

import org.w3.banana._
import org.w3.banana.jena._

package object vs  {

  type Rdf = Jena

  implicit val diesel: Diesel[Rdf] = JenaDiesel

  implicit val sparql: SparqlOps[Rdf] = JenaSparqlOps

  implicit val uriBinder: PointedGraphBinder[Rdf, Rdf#URI] = URIBinder.uriBinderForURI[Rdf].toNodeBinder.toPGB

}
