package org.w3.vs.model

import org.w3.vs.model._
import org.w3.banana._
import org.w3.banana.diesel._
import scalaz._
import scalaz.Scalaz._
import scalaz.Validation._
import org.joda.time.DateTime

/**
 * creates [EntityGraphBinder]s for the VS entities
 */
case class Binders[Rdf <: RDF](
  ops: RDFOperations[Rdf],
  union: GraphUnion[Rdf],
  graphTraversal: RDFGraphTraversal[Rdf]) {

  val diesel: Diesel[Rdf] = Diesel(ops, union, graphTraversal)
  
  import ops._
  import diesel._

  /* helper: to be moved */

  class IfDefined[S](s: S) {
    def ifDefined[T](opt: Option[T])(func: (S, T) => S) = opt match {
      case None => s
      case Some(t) => func(s, t)
    }
  }

  implicit def addIfDefinedMethod[S](s: S): IfDefined[S] = new IfDefined[S](s)

  /* uri builders for entities */

  object OrganizationUri extends PrefixBuilder("", "https://validator.w3.org/suite/organization/", ops) {
    def apply(id: OrganizationId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, OrganizationId] = getLocalName(uri) map OrganizationId.apply
  }

  object JobUri extends PrefixBuilder("", "https://validator.w3.org/suite/job/", ops) {
    def apply(id: JobId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, JobId] = getLocalName(uri) map JobId.apply
  }

  object UserUri extends PrefixBuilder("", "https://validator.w3.org/suite/user/", ops) {
    def apply(id: UserId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, UserId] = getLocalName(uri) map UserId.apply
  }

  object StrategyUri extends PrefixBuilder("", "https://validator.w3.org/suite/strategy/", ops) {
    def apply(id: StrategyId): Rdf#URI = apply(id.toString)
    def getId(uri: Rdf#URI): Validation[BananaException, StrategyId] = getLocalName(uri) map StrategyId.apply
  }

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

  /* binders for entities */

  val OrganizationVOBinder = new PointedGraphBinder[Rdf, OrganizationVO] {

    def toPointedGraph(t: OrganizationVO): PointedGraph[Rdf] = (
      OrganizationUri(t.id).a(organization.Organization)
        -- organization.name ->- t.name
        -- organization.admin ->- UserUri(t.admin)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, OrganizationVO] = {
      for {
        id <- pointed.node.asURI flatMap OrganizationUri.getId
        name <- (pointed / organization.name).exactlyOne.flatMap(_.as[String])
        adminId <- (pointed / organization.admin).exactlyOne.flatMap(_.asURI).flatMap(UserUri.getId)
      } yield {
        OrganizationVO(id, name, adminId)
      }
    }

  }

  val JobVOBinder = new PointedGraphBinder[Rdf, JobVO] {

    def toPointedGraph(t: JobVO): PointedGraph[Rdf] = (
      JobUri(t.id).a(job.Job)
        -- job.name ->- t.name
        -- job.createdOn ->- t.createdOn
        -- job.creator ->- UserUri(t.creatorId)
        -- job.organization ->- OrganizationUri(t.organizationId)
        -- job.strategy ->- StrategyUri(t.strategyId)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, JobVO] = {
      for {
        id <- pointed.node.asURI flatMap JobUri.getId
        name <- (pointed / job.name).exactlyOne.flatMap(_.as[String])
        createdOn <- (pointed / job.createdOn).exactlyOne.flatMap(_.as[DateTime])
        creator <- (pointed / job.creator).exactlyOne.flatMap(_.asURI) flatMap UserUri.getId
        organization <- (pointed / job.organization).exactlyOne.flatMap(_.asURI) flatMap OrganizationUri.getId
        strategy <- (pointed / job.strategy).exactlyOne.flatMap(_.asURI) flatMap StrategyUri.getId
      } yield {
        JobVO(id, name, createdOn, creator, organization, strategy)
      }
    }



  }



}






// case class Stores[Rdf <: RDF](
//   store: RDFStore[Rdf],
//   binders: Binders[Rdf]) {

//   import binders._

//   val OrganizationStore = EntityStore(store, OrganizationDataBinder)

// }
