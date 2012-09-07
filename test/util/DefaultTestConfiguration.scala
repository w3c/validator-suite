package org.w3.vs.util

import org.w3.vs._
import org.w3.banana.jena._
import com.hp.hpl.jena.tdb.TDBFactory.createDatasetGraph

trait DefaultTestConfiguration extends DefaultProdConfiguration {

  override lazy val store = JenaStore(createDatasetGraph())

}
