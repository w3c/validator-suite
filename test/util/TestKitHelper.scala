package org.w3.vs.util

import akka.testkit.{TestKitBase, ImplicitSender, TestKit}
import scala.concurrent.duration.Duration
import akka.actor.ActorSystem
import org.w3.vs._

trait TestKitHelper { this: TestKitBase =>

  def fishForMessagePF[T](max: Duration = Duration.Undefined, hint: String = "")(f: PartialFunction[Any, T]): T = {
    def pf: PartialFunction[Any, Boolean] = { case x => f.isDefinedAt(x) }
    val result = fishForMessage(max, hint)(pf)
    f(result)
  }

}
