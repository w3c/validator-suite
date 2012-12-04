package org.w3.vs.util

import org.w3.vs._
import org.w3.banana.jena._
import com.hp.hpl.jena.tdb.TDBFactory.createDatasetGraph
import scala.concurrent.ExecutionContext.Implicits.global

trait DefaultTestConfiguration extends DefaultProdConfiguration {

  override lazy val store = JenaStore(createDatasetGraph())

  override lazy val db = connection("vs-test")

}
