package org.w3.vs.model

import org.w3.banana._
import org.w3.banana.diesel._
import scalaz._
import scalaz.Scalaz._
import scalaz.Validation._

trait UriBuilders[Rdf <: RDF] {

  val ops: RDFOperations[Rdf]
  val diesel: Diesel[Rdf]
  
  import ops._
  import diesel._

  object AssertionUri extends PrefixBuilder("", "https://validator.w3.org/suite/assertion/", ops) {
    def apply(id: AssertionId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, AssertionId] = getLocalName(uri) map AssertionId.apply
  }

  object ContextUri extends PrefixBuilder("", "https://validator.w3.org/suite/context/", ops) {
    def apply(id: ContextId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, ContextId] = getLocalName(uri) map ContextId.apply
  }

  object AssertorResultUri extends PrefixBuilder("", "https://validator.w3.org/suite/assertorResult/", ops) {
    def apply(id: AssertorResponseId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, AssertorResponseId] = getLocalName(uri) map AssertorResponseId.apply
  }

  object JobUri extends PrefixBuilder("", "https://validator.w3.org/suite/job/", ops) {
    def apply(id: JobId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, JobId] = getLocalName(uri) map JobId.apply
  }

  object JobDataUri extends PrefixBuilder("", "https://validator.w3.org/suite/jobData/", ops) {
    def apply(id: JobDataId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, JobDataId] = getLocalName(uri) map JobDataId.apply
  }

  object OrganizationUri extends PrefixBuilder("", "https://validator.w3.org/suite/organization/", ops) {
    def apply(id: OrganizationId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, OrganizationId] = getLocalName(uri) map OrganizationId.apply
  }

  object ResourceResponseUri extends PrefixBuilder("", "https://validator.w3.org/suite/resourceResponse/", ops) {
    def apply(id: ResourceResponseId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, ResourceResponseId] = getLocalName(uri) map ResourceResponseId.apply
  }

  object RunUri extends PrefixBuilder("", "https://validator.w3.org/suite/run/", ops) {
    def apply(id: RunId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, RunId] = getLocalName(uri) map RunId.apply
  }

  object StrategyUri extends PrefixBuilder("", "https://validator.w3.org/suite/strategy/", ops) {
    def apply(id: StrategyId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, StrategyId] = getLocalName(uri) map StrategyId.apply
  }

  object UserUri extends PrefixBuilder("", "https://validator.w3.org/suite/user/", ops) {
    def apply(id: UserId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, UserId] = getLocalName(uri) map UserId.apply
  }

  object AssertorResponseUri extends PrefixBuilder("", "https://validator.w3.org/suite/assertorResponse/", ops) {
    def apply(id: AssertorResponseId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, AssertorResponseId] = getLocalName(uri) map AssertorResponseId.apply
  }

}
