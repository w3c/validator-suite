package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source

trait FromFileAssertor extends FromSourceAssertor {
  
  def assert(file: java.io.File): FutureVal[Throwable, Iterable[AssertionClosed]] = 
    FutureVal {
      Source.fromFile(file)
    } flatMap { source =>
      assert(source)
    }

}