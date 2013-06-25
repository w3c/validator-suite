package org.w3.vs.web

import scalaz.Equal
import scalaz.Scalaz._

case class Authority(underlying: String) extends AnyVal

object Authority {

  implicit val equalAuthority: Equal[Authority] = new Equal[Authority] {
    def equal(a1: Authority, a2: Authority) = a1.underlying === a2.underlying
  }

}
