package org.w3.vs.util

import org.w3.vs._
import org.w3.banana.jena._
import com.hp.hpl.jena.tdb.TDBFactory

trait DefaultTestConfiguration extends DefaultProdConfiguration {

  override lazy val blockingStore = JenaStore(TDBFactory.createDatasetGraph())

}
