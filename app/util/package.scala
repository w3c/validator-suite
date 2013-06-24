package org.w3.vs

import org.w3.vs.web._

package object util {
  
  type Host = String
  type Protocol = String
  type Authority = String
  type Port = Int
  type FileName = String
  
  type ContentType = String

  /*******/

  import com.ning.http.client.Response
  import java.util.{ Map => jMap, List => jList }
  import org.w3.vs.model.{ HttpMethod, HttpResponse }
  import scalax.io.{ Resource, InputResource }
  import java.io.{ InputStream, ByteArrayInputStream }

  implicit class ResponseW(val response: Response) extends AnyVal {
    def asHttpResponse(url: URL, method: HttpMethod): (HttpResponse, InputResource[InputStream]) = {
      val status = response.getStatusCode()
      val headers = Headers(response.getHeaders())
      def bodyContent = Resource.fromInputStream(response.getResponseBodyAsStream())
      val httpResponse = HttpResponse(url, method, status, headers, bodyContent)
      (httpResponse, bodyContent)
    }
  }

  /*******/

  import scala.math.Ordering
  import org.joda.time.DateTime

  implicit val DateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan((x: DateTime, y: DateTime) => x isBefore y)

  import scalaz.Equal

  implicit val equalDateTime: Equal[DateTime] = new Equal[DateTime] {
    def equal(a1: DateTime, a2: DateTime) = DateTimeOrdering.compare(a1, a2) == 0
  }
  
  def shortId(id: String): String = id.substring(0, 6)

  /*******/

  import java.net.{ URL => jURL }
  import scalaz.Scalaz._

  implicit val equaljURL: Equal[jURL] = new Equal[jURL] {
    def equal(a1: jURL, a2: jURL) = a1.toExternalForm === a2.toExternalForm
  }

  trait Out {
    def println(s: String): Unit
  }

  trait In {
    def readLine(): String
  }

}
