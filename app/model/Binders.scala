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
        AssertionUri(t.id).a(ont.Assertion)
          -- ont.url ->- t.url
          -- ont.lang ->- t.lang
          -- ont.title ->- t.title
          -- ont.severity ->- t.severity
          -- ont.assertorResponseId ->- AssertorResponseUri(t.assertorResponseId)
      )
      pointed.ifDefined(t.description){ (p, desc) => p -- ont.description ->- desc }

    }

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, AssertionVO] = {
      for {
        id <- pointed.node.asURI flatMap AssertionUri.getId
        url <- (pointed / ont.url).exactlyOne.flatMap(_.as[URL])
        lang <- (pointed / ont.lang).exactlyOne.flatMap(_.as[String])
        title <- (pointed / ont.title).exactlyOne.flatMap(_.as[String])
        severity <- (pointed / ont.severity).exactlyOne.flatMap(_.as[AssertionSeverity])
        description <- (pointed / ont.description).headOption match {
          case None => Success(None)
          case Some(pg) => pg.node.as[String] map (Some(_))
        }
        assertorResponseId <- (pointed / ont.assertorResponseId).exactlyOne.flatMap(_.asURI).flatMap(AssertorResponseUri.getId)
      } yield {
        AssertionVO(id, url, lang, title, severity, description, assertorResponseId)
      }
    }

  }


  val ContextVOBinder = new PointedGraphBinder[Rdf, ContextVO] {

    def toPointedGraph(t: ContextVO): PointedGraph[Rdf] = {
      val pointed = (
        ContextUri(t.id).a(ont.Context)
          -- ont.content ->- t.content
          -- ont.assertionId ->- AssertionUri(t.assertionId)
      )
      pointed
        .ifDefined(t.line){ (p, l) => p -- ont.line ->- l }
        .ifDefined(t.column){ (p, c) => p -- ont.column ->- c }

    }

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ContextVO] = {
      for {
        id <- pointed.node.asURI flatMap ContextUri.getId
        content <- (pointed / ont.content).exactlyOne.flatMap(_.as[String])
        line <- (pointed / ont.line).headOption match {
          case None => Success(None)
          case Some(pg) => pg.node.as[Int] map (Some(_))
        }
        column <- (pointed / ont.column).headOption match {
          case None => Success(None)
          case Some(pg) => pg.node.as[Int] map (Some(_))
        }
        assertionId <- (pointed / ont.assertionId).exactlyOne.flatMap(_.asURI).flatMap(AssertionUri.getId)
      } yield {
        ContextVO(id, content, line, column, assertionId)
      }
    }

  }



  val AssertorResultVOBinder = new PointedGraphBinder[Rdf, AssertorResultVO] {

    def toPointedGraph(t: AssertorResultVO): PointedGraph[Rdf] = (
      AssertorResponseUri(t.id).a(ont.AssertorResult)
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.runId ->- RunUri(t.runId)
        -- ont.assertorId ->- AssertorUri(t.assertorId)
        -- ont.sourceUrl ->- t.sourceUrl
        -- ont.timestamp ->- t.timestamp
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, AssertorResultVO] = {
      for {
        id <- pointed.node.asURI flatMap AssertorResponseUri.getId
        jobId <- (pointed / ont.jobId).exactlyOne.flatMap(_.asURI).flatMap(JobUri.getId)
        runId <- (pointed / ont.runId).exactlyOne.flatMap(_.asURI).flatMap(RunUri.getId)
        assertorId <- (pointed / ont.assertorId).exactlyOne.flatMap(_.asURI).flatMap(AssertorUri.getId)
        sourceUrl <- (pointed / ont.sourceUrl).exactlyOne.flatMap(_.as[URL])
        timestamp <- (pointed / ont.timestamp).exactlyOne.flatMap(_.as[DateTime])
      } yield {
        AssertorResultVO(id, jobId, runId, assertorId, sourceUrl, timestamp)
      }
    }

  }



  val JobVOBinder = new PointedGraphBinder[Rdf, JobVO] {

    def toPointedGraph(t: JobVO): PointedGraph[Rdf] = (
      JobUri(t.id).a(ont.Job)
        -- ont.name ->- t.name
        -- ont.createdOn ->- t.createdOn
        -- ont.creator ->- UserUri(t.creatorId)
        -- ont.organization ->- OrganizationUri(t.organizationId)
        -- ont.strategy ->- StrategyUri(t.strategyId)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, JobVO] = {
      for {
        id <- pointed.node.asURI flatMap JobUri.getId
        name <- (pointed / ont.name).exactlyOne.flatMap(_.as[String])
        createdOn <- (pointed / ont.createdOn).exactlyOne.flatMap(_.as[DateTime])
        creator <- (pointed / ont.creator).exactlyOne.flatMap(_.asURI) flatMap UserUri.getId
        organization <- (pointed / ont.organization).exactlyOne.flatMap(_.asURI) flatMap OrganizationUri.getId
        strategy <- (pointed / ont.strategy).exactlyOne.flatMap(_.asURI) flatMap StrategyUri.getId
      } yield {
        JobVO(id, name, createdOn, creator, organization, strategy)
      }
    }

  }




  val JobDataVOBinder = new PointedGraphBinder[Rdf, JobDataVO] {

    def toPointedGraph(t: JobDataVO): PointedGraph[Rdf] = (
      JobDataUri(t.id).a(ont.JobData)
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.runId ->- RunUri(t.runId)
        -- ont.resources ->- t.resources
        -- ont.errors ->- t.errors
        -- ont.warnings ->- t.warnings
        -- ont.timestamp ->- t.timestamp
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, JobDataVO] = {
      for {
        id <- pointed.node.asURI flatMap JobDataUri.getId
        jobId <- (pointed / ont.jobId).exactlyOne.flatMap(_.asURI).flatMap(JobUri.getId)
        runId <- (pointed / ont.runId).exactlyOne.flatMap(_.asURI).flatMap(RunUri.getId)
        resources <- (pointed / ont.resources).exactlyOne.flatMap(_.as[Int])
        errors <- (pointed / ont.errors).exactlyOne.flatMap(_.as[Int])
        warnings <- (pointed / ont.warnings).exactlyOne.flatMap(_.as[Int])
        timestamp <- (pointed / ont.timestamp).exactlyOne.flatMap(_.as[DateTime])
      } yield {
        JobDataVO(id, jobId, runId, resources, errors, warnings, timestamp)
      }
    }

  }




  val OrganizationVOBinder = new PointedGraphBinder[Rdf, OrganizationVO] {

    def toPointedGraph(t: OrganizationVO): PointedGraph[Rdf] = (
      OrganizationUri(t.id).a(ont.Organization)
        -- ont.name ->- t.name
        -- ont.admin ->- UserUri(t.admin)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, OrganizationVO] = {
      for {
        id <- pointed.node.asURI flatMap OrganizationUri.getId
        name <- (pointed / ont.name).exactlyOne.flatMap(_.as[String])
        adminId <- (pointed / ont.admin).exactlyOne.flatMap(_.asURI).flatMap(UserUri.getId)
      } yield {
        OrganizationVO(id, name, adminId)
      }
    }

  }



  // val ResourceResponseVOBinder = new PointedGraphBinder[Rdf, ResourceResponseVO] {

  //   def toPointedGraph(t: ResourceResponseVO): PointedGraph[Rdf] = (
  //     OrganizationUri(t.id).a(organization.Organization)
  //       -- organization.name ->- t.name
  //       -- organization.admin ->- UserUri(t.admin)
  //   )

  //   def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ResourceResponseVO] = {
  //     for {
  //       id <- pointed.node.asURI flatMap OrganizationUri.getId
  //       name <- (pointed / organization.name).exactlyOne.flatMap(_.as[String])
  //       adminId <- (pointed / organization.admin).exactlyOne.flatMap(_.asURI).flatMap(UserUri.getId)
  //     } yield {
  //       OrganizationVO(id, name, adminId)
  //     }
  //   }

  // }




}






// case class Stores[Rdf <: RDF](
//   store: RDFStore[Rdf],
//   binders: Binders[Rdf]) {

//   import binders._

//   val OrganizationStore = EntityStore(store, OrganizationDataBinder)

// }
