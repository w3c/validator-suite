package org.w3.vs.store

import org.w3.vs.model._
import org.w3.banana._
import scalaz._

trait UriBuilders[Rdf <: RDF] {
self: Binders[Rdf] =>

  import diesel._
  import ops._

  implicit object AssertionUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/assertion/", ops)
  with URIBinder[Rdf, AssertionId] {
    def apply(id: AssertionId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, AssertionId] =
      getLocalName(uri) map AssertionId.apply
    def toUri(t: AssertionId): Rdf#URI = apply(t)
  }

  implicit object ContextUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/context/", ops)
  with URIBinder[Rdf, ContextId] {
    def apply(id: ContextId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, ContextId] = getLocalName(uri) map ContextId.apply
    def toUri(t: ContextId): Rdf#URI = apply(t)
  }

  implicit object AssertorResultUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/assertorResult/", ops)
  with URIBinder[Rdf, AssertorResponseId] {
    def apply(id: AssertorResponseId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, AssertorResponseId] = getLocalName(uri) map AssertorResponseId.apply
    def toUri(t: AssertorResponseId): Rdf#URI = apply(t)
  }

  implicit object JobUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/job/", ops)
  with URIBinder[Rdf, JobId] {
    def apply(id: JobId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, JobId] = getLocalName(uri) map JobId.apply
    def toUri(t: JobId): Rdf#URI = apply(t)
  }

  implicit object JobDataUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/jobData/", ops)
  with URIBinder[Rdf, JobDataId] {
    def apply(id: JobDataId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, JobDataId] = getLocalName(uri) map JobDataId.apply
    def toUri(t: JobDataId): Rdf#URI = apply(t)
  }

  implicit object OrganizationUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/organization/", ops)
  with URIBinder[Rdf, OrganizationId] {
    def apply(id: OrganizationId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, OrganizationId] = getLocalName(uri) map OrganizationId.apply
    def toUri(t: OrganizationId): Rdf#URI = apply(t)
  }

  implicit object ResourceResponseUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/resourceResponse/", ops)
  with URIBinder[Rdf, ResourceResponseId] {
    def apply(id: ResourceResponseId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, ResourceResponseId] = getLocalName(uri) map ResourceResponseId.apply
    def toUri(t: ResourceResponseId): Rdf#URI = apply(t)
  }

  implicit object RunUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/run/", ops)
  with URIBinder[Rdf, RunId] {
    def apply(id: RunId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, RunId] = getLocalName(uri) map RunId.apply
    def toUri(t: RunId): Rdf#URI = apply(t)
  }

  implicit object StrategyUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/strategy/", ops)
  with URIBinder[Rdf, StrategyId] {
    def apply(id: StrategyId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, StrategyId] = getLocalName(uri) map StrategyId.apply
    def toUri(t: StrategyId): Rdf#URI = apply(t)
  }

  implicit object UserUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/user/", ops)
  with URIBinder[Rdf, UserId] {
    def apply(id: UserId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, UserId] = getLocalName(uri) map UserId.apply
    def toUri(t: UserId): Rdf#URI = apply(t)
  }

  implicit object AssertorResponseUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/assertorResponse/", ops)
  with URIBinder[Rdf, AssertorResponseId] {
    def apply(id: AssertorResponseId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, AssertorResponseId] = getLocalName(uri) map AssertorResponseId.apply
    def toUri(t: AssertorResponseId): Rdf#URI = apply(t)
  }

  implicit object AssertorUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/assertor/", ops)
  with URIBinder[Rdf, AssertorId] {
    def apply(id: AssertorId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, AssertorId] = getLocalName(uri) map AssertorId.apply
    def toUri(t: AssertorId): Rdf#URI = apply(t)
  }

}
