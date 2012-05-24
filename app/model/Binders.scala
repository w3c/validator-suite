package org.w3.vs.store

import org.w3.vs.model._
import org.w3.banana._
import org.w3.banana.diesel._
import scalaz._
import scalaz.Scalaz._
import scalaz.Validation._

/**
 * creates [EntityGraphBinder]s for the VS entities
 */
case class Binders[Rdf <: RDF](
  ops: RDFOperations[Rdf],
  union: GraphUnion[Rdf],
  graphTraversal: RDFGraphTraversal[Rdf]) {

  val diesel = Diesel(ops, union, graphTraversal)
  
  import ops._
  import diesel._

  /* uri builders for entities */

  object OrganizationUri extends PrefixBuilder("", "https://validator.w3.org/suite/organization/", ops) {
    def apply(id: OrganizationId): Rdf#URI = apply(id.toString)
  }

  object JobUri extends PrefixBuilder("", "https://validator.w3.org/suite/job/", ops) {
    def apply(id: JobId): Rdf#URI = apply(id.toString)
  }

  object UserUri extends PrefixBuilder("", "https://validator.w3.org/suite/user/", ops) {
    def apply(id: UserId): Rdf#URI = apply(id.toString)
  }

  object StrategyUri extends PrefixBuilder("", "https://validator.w3.org/suite/strategy/", ops) {
    def apply(id: StrategyId): Rdf#URI = apply(id.toString)
  }

  /* ontologies for entities */

  object organization extends PrefixBuilder("organization", "https://validator.w3.org/suite/organization#", ops) {
    val name = apply("name")
    val Organization = apply("Organization")
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
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, OrganizationVO] = {
      for {
        id <- pointed.node.asURI flatMap OrganizationUri.getLocalName map OrganizationId.apply
        name <- (pointed / organization.name).exactlyOne.flatMap(_.as[String])
        adminId <- (pointed / organization.name).exactlyOne.flatMap(_.asURI).flatMap(UserUri.getLocalName).map(UserId(_))
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
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, JobVO] = null



//     def toGraph(t: Job): Rdf#Graph = (
//       toUri(t).a(job.Job)
//         -- job.name ->- t.name
// //        -- job.creator ->- userUri()
//     ).graph

//     def toUri(t: Job): Rdf#URI = JobUri(t.id)

  }



}






// case class Stores[Rdf <: RDF](
//   store: RDFStore[Rdf],
//   binders: Binders[Rdf]) {

//   import binders._

//   val OrganizationStore = EntityStore(store, OrganizationDataBinder)

// }
