package org.w3.vs.model

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
  graphTraversal: RDFGraphTraversal[Rdf])
extends UriBuilders[Rdf] with Ontologies[Rdf] {

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
