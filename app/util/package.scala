package org.w3

package object util {
  
  type Host = String
  type Protocol = String
  type Authority = String
  type Port = Int
  type FileName = String
  
  type ContentType = String

  // should be collection.immutable.ListMap to preserver order insertion
  type Headers = Map[String, List[String]]
  object Headers { val DEFAULT_CHARSET = "UTF-8" }
  implicit def wrapHeaders(headers: Headers): HeadersW = new HeadersW(headers)

  import scala.math.Ordering
  import org.joda.time.DateTime

  implicit val DateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan((x: DateTime, y: DateTime) => x isBefore y)

  import scalaz.Equal

  implicit val equalDateTime: Equal[DateTime] = new Equal[DateTime] {
    def equal(a1: DateTime, a2: DateTime) = DateTimeOrdering.compare(a1, a2) == 0
  }
  
  def shortId(id: String): String = id.substring(0, 6)

}
