package org.w3.util

import org.w3.banana._
import org.w3.banana.jena._

object Jena {

  def dump[Rdf <: RDF](graph: Rdf#Graph)(implicit ops: RDFOperations[Rdf]): Unit = {
    val mToJena = new RDFTransformer[Rdf, Jena](ops, JenaOperations)
    val jenaGraph = mToJena.transform(graph)
    println(JenaTurtleWriter.asString(jenaGraph, ""))
  }

}
