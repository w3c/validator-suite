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

    def toPointedGraph(t: AssertionVO): PointedGraph[Rdf] = (
      AssertionUri(t.id).a(ont.Assertion)
        -- ont.url ->- t.url
        -- ont.lang ->- t.lang
        -- ont.title ->- t.title
        -- ont.severity ->- t.severity
        -- ont.description ->- t.description
        -- ont.assertorResponseId ->- AssertorResponseUri(t.assertorResponseId)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, AssertionVO] = {
      for {
        id <- pointed.node.asUri flatMap AssertionUri.getId
        url <- (pointed / ont.url).exactlyOne[URL]
        lang <- (pointed / ont.lang).exactlyOne[String]
        title <- (pointed / ont.title).exactlyOne[String]
        severity <- (pointed / ont.severity).exactlyOne[AssertionSeverity]
        description <- (pointed / ont.description).asOption[String]
        assertorResponseId <- (pointed / ont.assertorResponseId).exactlyOneUri.flatMap(AssertorResponseUri.getId)
      } yield {
        AssertionVO(id, url, lang, title, severity, description, assertorResponseId)
      }
    }

  }


  val ContextVOBinder = new PointedGraphBinder[Rdf, ContextVO] {

    def toPointedGraph(t: ContextVO): PointedGraph[Rdf] = (
      ContextUri(t.id).a(ont.Context)
        -- ont.content ->- t.content
        -- ont.line ->- t.line
        -- ont.column ->- t.column
        -- ont.assertionId ->- AssertionUri(t.assertionId)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ContextVO] = {
      for {
        id <- pointed.node.asUri flatMap ContextUri.getId
        content <- (pointed / ont.content).exactlyOne[String]
        line <- (pointed / ont.line).asOption[Int]
        column <- (pointed / ont.column).asOption[Int]
        assertionId <- (pointed / ont.assertionId).exactlyOneUri.flatMap(AssertionUri.getId)
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
        id <- pointed.node.asUri flatMap AssertorResponseUri.getId
        jobId <- (pointed / ont.jobId).exactlyOneUri.flatMap(JobUri.getId)
        runId <- (pointed / ont.runId).exactlyOneUri.flatMap(RunUri.getId)
        assertorId <- (pointed / ont.assertorId).exactlyOneUri.flatMap(AssertorUri.getId)
        sourceUrl <- (pointed / ont.sourceUrl).exactlyOne[URL]
        timestamp <- (pointed / ont.timestamp).exactlyOne[DateTime]
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
        id <- pointed.node.asUri flatMap JobUri.getId
        name <- (pointed / ont.name).exactlyOne[String]
        createdOn <- (pointed / ont.createdOn).exactlyOne[DateTime]
        creator <- (pointed / ont.creator).exactlyOneUri flatMap UserUri.getId
        organization <- (pointed / ont.organization).exactlyOneUri flatMap OrganizationUri.getId
        strategy <- (pointed / ont.strategy).exactlyOneUri flatMap StrategyUri.getId
      } yield {
        JobVO(id, name, createdOn, creator, organization, strategy)
      }
    }

  }




  val JobDataVOBinder = new PointedGraphBinder[Rdf, JobDataVO] {

    def toPointedGraph(t: JobDataVO): PointedGraph[Rdf] = (
      JobDataUri(t.id).a(ont.JobData)
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.resources ->- t.resources
        -- ont.errors ->- t.errors
        -- ont.warnings ->- t.warnings
        -- ont.timestamp ->- t.timestamp
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, JobDataVO] = {
      for {
        id <- pointed.node.asUri flatMap JobDataUri.getId
        jobId <- (pointed / ont.jobId).exactlyOneUri.flatMap(JobUri.getId)
        resources <- (pointed / ont.resources).exactlyOne[Int]
        errors <- (pointed / ont.errors).exactlyOne[Int]
        warnings <- (pointed / ont.warnings).exactlyOne[Int]
        timestamp <- (pointed / ont.timestamp).exactlyOne[DateTime]
      } yield {
        JobDataVO(id, jobId, resources, errors, warnings, timestamp)
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
        id <- pointed.node.asUri flatMap OrganizationUri.getId
        name <- (pointed / ont.name).exactlyOne[String]
        adminId <- (pointed / ont.admin).exactlyOneUri.flatMap(UserUri.getId)
      } yield {
        OrganizationVO(id, name, adminId)
      }
    }

  }


  val ErrorResponseVOBinder = new PointedGraphBinder[Rdf, ErrorResponseVO] {

    def toPointedGraph(t: ErrorResponseVO): PointedGraph[Rdf] = (
      ResourceResponseUri(t.id).a(ont.ErrorResponse)
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.runId ->- RunUri(t.runId)
        -- ont.url ->- t.url
        -- ont.action ->- t.action
        -- ont.timestamp ->- t.timestamp
        -- ont.why ->- t.why
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ErrorResponseVO] = {
      for {
        id <- pointed.node.asUri flatMap ResourceResponseUri.getId
        jobId <- (pointed / ont.jobId).exactlyOneUri.flatMap(JobUri.getId)
        runId <- (pointed / ont.runId).exactlyOneUri.flatMap(RunUri.getId)
        url <- (pointed / ont.url).exactlyOne[URL]
        action <- (pointed / ont.action).exactlyOne[HttpAction]
        timestamp <- (pointed / ont.timestamp).exactlyOne[DateTime]
        why <- (pointed / ont.why).exactlyOne[String]
      } yield {
        ErrorResponseVO(id, jobId, runId, url, action, timestamp, why)
      }
    }

  }








  val ResourceResponseVOBinder = new PointedGraphBinder[Rdf, ResourceResponseVO] {

    def toPointedGraph(t: ResourceResponseVO): PointedGraph[Rdf] = null
 // (
 //      OrganizationUri(t.id).a(organization.Organization)
 //        -- organization.name ->- t.name
 //        -- organization.admin ->- UserUri(t.admin)
 //    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ResourceResponseVO] = {
      // for {
      //   id <- pointed.node.asURI flatMap OrganizationUri.getId
      //   name <- (pointed / organization.name).exactlyOne[String])
      //   adminId <- (pointed / organization.admin).exactlyOne.flatMap(_.asURI).flatMap(UserUri.getId)
      // } yield {
      //   OrganizationVO(id, name, adminId)
      // }
      null
    }

  }





//  val ResourceResponseVOBinder = new PointedGraphBinder[Rdf, ResourceResponseVO] {
//
//    def toPointedGraph(t: ResourceResponseVO): PointedGraph[Rdf] = null
//
//    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ResourceResponseVO] = {
//      null
//    }
//
//  }
//





}






// case class Stores[Rdf <: RDF](
//   store: RDFStore[Rdf],
//   binders: Binders[Rdf]) {

//   import binders._

//   val OrganizationStore = EntityStore(store, OrganizationDataBinder)

// }
