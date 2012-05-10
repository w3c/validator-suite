package org.w3.vs.store

import org.w3.vs.model._
import org.w3.banana._
import org.w3.banana.diesel._

/**
 * creates [EntityGraphBinder]s for the VS entities
 */
class Binders[Rdf <: RDF](
  ops: RDFOperations[Rdf],
  union: GraphUnion[Rdf],
  graphTraversal: RDFGraphTraversal[Rdf]) {

  import ops._
  val diesel = Diesel(ops, union, graphTraversal)
  import diesel._

  object organization extends PrefixBuilder("organization", "https://validator.w3.org/suite/organization#", ops) {
    val name = apply("name")
    val Organization = apply("Organization")
  }

  val OrganizationDataBinder = new EntityGraphBinder[Rdf, OrganizationData] {

    val base = Prefix("", "https://validator.w3.org/suite/organization/", ops)

    def fromGraph(uri: Rdf#IRI, graph: Rdf#Graph): OrganizationData = {
      val ng = GraphNode(uri, graph)
      val base(id) = uri
      val name = (ng / organization.name).asString getOrElse sys.error("")
      OrganizationData(OrganizationId.fromString(id), name)
    }

    def toGraph(t: OrganizationData): Rdf#Graph = (
      toUri(t).a(organization.Organization)
        -- organization.name ->- t.name
    ).graph

    def toUri(t: OrganizationData): Rdf#IRI = base(t.id.toString)

  }


  


}
