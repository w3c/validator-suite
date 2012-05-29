package org.w3.vs.model

import org.w3.banana._
import org.w3.banana.diesel._
import scalaz._
import scalaz.Scalaz._
import scalaz.Validation._

trait Ontologies[Rdf <: RDF] {

  val ops: RDFOperations[Rdf]
  val diesel: Diesel[Rdf]
  
  import ops._
  import diesel._

  /* ontologies for entities */

  object organization extends PrefixBuilder("organization", "https://validator.w3.org/suite/organization#", ops) {
    val Organization = apply("Organization")
    val name = apply("name")
    val admin = apply("admin")
  }

  object job extends PrefixBuilder("job", "https://validator.w3.org/suite/job#", ops) {
    val Job = apply("Job")
    val name = apply("name")
    val creator = apply("creator")
    val organization = apply("organization")
    val strategy = apply("strategy")
    val createdOn = apply("created-on")
  }

}
