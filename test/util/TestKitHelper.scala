package org.w3.vs.util

import akka.testkit.TestKit
import akka.util.Duration

trait TestKitHelper { this: TestKit =>

  def fishForMessagePF[T](max: Duration = Duration.Undefined, hint: String = "")(f: PartialFunction[Any, T]): T = {
    def pf: PartialFunction[Any, Boolean] = { case x => f.isDefinedAt(x) }
    val result = fishForMessage(max, hint)(pf)
    f(result)
  }

}
