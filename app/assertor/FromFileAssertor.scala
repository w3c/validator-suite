package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source

object FromFileAssertor extends FromSourceAssertor with UnicornFormatAssertor {
  
  val key = "from-file-assertor"

  def assert(file: java.io.File): Iterable[Assertion] = {
    val source = Source.fromFile(file)
    assert(source)
  }

}
