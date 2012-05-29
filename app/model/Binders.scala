package org.w3.vs.model

import org.w3.banana._
import org.w3.banana.diesel._
import scalaz._
import scalaz.Scalaz._
import scalaz.Validation._
import org.joda.time.DateTime
import org.w3.util.URL

/**
 * creates [EntityGraphBinder]s for the VS entities
 */
case class Binders[Rdf <: RDF](
  ops: RDFOperations[Rdf],
  union: GraphUnion[Rdf],
  graphTraversal: RDFGraphTraversal[Rdf])
extends UriBuilders[Rdf] with Ontologies[Rdf] with LiteralBinders[Rdf] {

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

  val AssertionVOBinder = new PointedGraphBinder[Rdf, AssertionVO] {

    def toPointedGraph(t: AssertionVO): PointedGraph[Rdf] = {
      val pointed = (
        AssertionUri(t.id).a(assertion.Assertion)
          -- assertion.url ->- t.url
          -- assertion.lang ->- t.lang
          -- assertion.title ->- t.title
          -- assertion.severity ->- t.severity
          -- assertion.assertorResponseId ->- AssertorResponseUri(t.assertorResponseId)
      )
      pointed.ifDefined(t.description){ (p, desc) => p -- assertion.description ->- desc }

    }

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, AssertionVO] = {
      for {
        id <- pointed.node.asURI flatMap AssertionUri.getId
        url <- (pointed / assertion.url).exactlyOne.flatMap(_.as[URL])
        lang <- (pointed / assertion.lang).exactlyOne.flatMap(_.as[String])
        title <- (pointed / assertion.title).exactlyOne.flatMap(_.as[String])
        severity <- (pointed / assertion.severity).exactlyOne.flatMap(_.as[AssertionSeverity])
        description <- (pointed / assertion.description).headOption match {
          case None => Success(None)
          case Some(pg) => pg.node.as[String] map (Some(_))
        }
        assertorResponseId <- (pointed / assertion.assertorResponseId).exactlyOne.flatMap(_.asURI).flatMap(AssertorResponseUri.getId)
      } yield {
        AssertionVO(id, url, lang, title, severity, description, assertorResponseId)
      }
    }

  }

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
