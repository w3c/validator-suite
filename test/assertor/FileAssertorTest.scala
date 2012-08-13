package org.w3.vs.assertor

import org.w3.util._
import org.specs2.mutable._
import org.w3.vs.model._

object FileRunTest extends Specification {

  "test_1.xml should has only 3 info events" in {
    val testFile = new java.io.File("test/resources/test_1.xml")
    val assertions = FromFileAssertor.assert(testFile)
    assertions forall { _.severity must be(Info) }
    assertions must have size(3)
  }


}
