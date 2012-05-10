package org.w3.vs.store

import org.scalatest.{Filter => _, _}
import org.scalatest.matchers.MustMatchers
import java.io._
import org.w3.vs.model._
import org.w3.util.{File => _, _}
import org.w3.vs.run._
import java.nio.file.Paths

import org.w3.banana._

abstract class BindersSpec[Rdf <: RDF](binders: Binders[Rdf]) extends WordSpec with MustMatchers with EitherValues {

  import binders._

  "foo" in {

    val organization = OrganizationData(OrganizationId(), "W3C")
    
    import OrganizationDataBinder._

    val graph = toGraph(organization)
    val uri = toUri(organization)
    val organization2 = fromGraph(uri, graph)

    organization2 must be === (organization)

  }


}

import org.w3.banana.jena._

object JenaBinders extends Binders(JenaOperations, JenaGraphUnion, JenaGraphTraversal)

class JenaBindersSpec() extends BindersSpec(JenaBinders)
