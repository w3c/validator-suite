package org.w3.vs.store

import org.w3.banana._

trait Ontologies[Rdf <: RDF] {
self: Binders[Rdf] =>

  import diesel._
  import ops._

  object ont extends PrefixBuilder("ont", "https://validator.w3.org/suite/ontology#", ops) {
    val Assertion = apply("Assertion")
    val url = apply("url")
    val lang = apply("lang")
    val title = apply("title")
    val severity = apply("severity")
    val description = apply("description")
    val assertorResponseId = apply("assertorResponseId")

    val Context = apply("Context")
    val content = apply("content")
    val line = apply("line")
    val column = apply("column")
    val assertionId = apply("assertionId")

    val AssertorResult = apply("AssertorResult")
    val jobId = apply("jobId")
    val runId = apply("runId")
    val assertorId = apply("assertorId")
    val sourceUrl = apply("sourceUrl")
    val timestamp = apply("timestamp")

    val Job = apply("Job")
    val name = apply("name")
    val creator = apply("creator")
    val organization = apply("organization")
    val strategy = apply("strategy")
    val createdOn = apply("createdOn")

    val JobData = apply("JobData")
    val resources = apply("resources")
    val errors = apply("errors")
    val warnings = apply("warnings")

    val Organization = apply("Organization")
    val admin = apply("admin")

    val ResourceResponse = apply("ResourceResponse")
    val ErrorResponse = apply("ErrorResponse")
    val HttpResponse = apply("HttpResponse")
    val action = apply("action")
    val why = apply("why")
    val status = apply("status")
    val headers = apply("headers")
    val extractedURLs = apply("extractedURLs")

    val Run = apply("Run")
    val explorationMode = apply("explorationMode")
    val createdAt = apply("createdAt")
    val completedAt = apply("completedAt")

    val Strategy = apply("Strategy")
    val entrypoint = apply("entrypoint")
    val linkCheck = apply("linkCheck")
    val maxResources = apply("maxResources")
    val assertorSelector = apply("assertorSelector")
    
    val User = apply("User")
    val email = apply("email")
    val password = apply("password")
    val organizationId = apply("organizationId")

    val AssertorSelector = apply("AssertorSelector")
    val map = apply("map")
    
  }


}
