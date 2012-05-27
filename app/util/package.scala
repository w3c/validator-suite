package org.w3

import java.net.{URL => jURL}

package object util {
  
  type Host = String
  type Protocol = String
  type Authority = String
  type Port = Int
  type File = String
  
  type Headers = Map[String, List[String]]
  type ContentType = String
  
  import scala.math.Ordering
  import org.joda.time.DateTime

  implicit val DateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan((x: DateTime, y: DateTime) => x isBefore y)

  import scalaz.Equal

  implicit val equalDateTime: Equal[DateTime] = new Equal[DateTime] {
    def equal(a1: DateTime, a2: DateTime) = DateTimeOrdering.compare(a1, a2) == 0
  }

}
