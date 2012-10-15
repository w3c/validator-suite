package org.w3

import java.net.{ URL => jURL }

package object util extends HeadersImplicits {
  
  type Host = String
  type Protocol = String
  type Authority = String
  type Port = Int
  type FileName = String
  
  // should be collection.immutable.ListMap to preserver order insertion
  type Headers = Map[String, List[String]]
  type ContentType = String
  
  import scala.math.Ordering
  import org.joda.time.DateTime

  implicit val DateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan((x: DateTime, y: DateTime) => x isBefore y)

  import scalaz.Equal

  implicit val equalDateTime: Equal[DateTime] = new Equal[DateTime] {
    def equal(a1: DateTime, a2: DateTime) = DateTimeOrdering.compare(a1, a2) == 0
  }

  import org.w3.banana._
  import org.w3.vs.VSConfiguration
  import java.util.concurrent.TimeoutException
  import scala.concurrent._
  
  def shortId(id: String): String = id.substring(0, 6)

}
