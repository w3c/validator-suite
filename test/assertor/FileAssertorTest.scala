package org.w3.vs.assertor

import org.w3.vs.util._
import org.w3.vs.model._
import org.scalatest._
import org.scalatest.Matchers

class FileRunTest extends WordSpec with Matchers {

  "test_1.xml should has only 3 info events" in {
    val testFile = new java.io.File("test/resources/test_1.xml")
    val assertions = FromFileAssertor.assert(testFile)
    assertions foreach { _.severity should be(Info) }
    assertions should have size(3)
  }


}
