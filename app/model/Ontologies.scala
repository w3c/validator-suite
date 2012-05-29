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

  object assertion extends PrefixBuilder("assertion", "https://validator.w3.org/suite/assertion#", ops) {
    val Assertion = apply("Assertion")
  }

  object context extends PrefixBuilder("context", "https://validator.w3.org/suite/context#", ops) {
    val Context = apply("Context")
  }

  object assertorResult extends PrefixBuilder("assertorResult", "https://validator.w3.org/suite/assertorResult#", ops) {
    val AssertorResult = apply("AssertorResult")
  }

  object job extends PrefixBuilder("job", "https://validator.w3.org/suite/job#", ops) {
    val Job = apply("Job")
    val name = apply("name")
    val creator = apply("creator")
    val organization = apply("organization")
    val strategy = apply("strategy")
    val createdOn = apply("createdOn")
  }

  object jobData extends PrefixBuilder("jobData", "https://validator.w3.org/suite/jobData#", ops) {
    val JobData = apply("JobData")
  }

  object organization extends PrefixBuilder("organization", "https://validator.w3.org/suite/organization#", ops) {
    val Organization = apply("Organization")
    val name = apply("name")
    val admin = apply("admin")
  }

  object resourceResponse extends PrefixBuilder("resourceResponse", "https://validator.w3.org/suite/resourceResponse#", ops) {
    val ResourceResponse = apply("ResourceResponse")
  }

  object run extends PrefixBuilder("run", "https://validator.w3.org/suite/run#", ops) {
    val Run = apply("run")
  }

  object strategy extends PrefixBuilder("strategy", "https://validator.w3.org/suite/strategy#", ops) {
    val Strategy = apply("Strategy")
    
  }

  object user extends PrefixBuilder("user", "https://validator.w3.org/suite/user#", ops) {
    val User = apply("User")
    
  }


}
