package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.iteratee._
import org.w3.vs.util.website._
import org.w3.vs.web._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.timer._
import javax.servlet.http._
import play.api.libs.iteratee._
import scala.util.Try
import org.w3.vs._
import play.api.Mode

class WebsiteWithInvalidRedirectCrawlTest extends VSTest with ServersTest with TestData with WipeoutData {

  implicit val vs = new ValidatorSuite { val mode = Mode.Test }

  val servlet = new HttpServlet {
    override def doGet(req: HttpServletRequest, resp: HttpServletResponse) = {
      req.getRequestURI match {
        case "/" => {
          resp.setStatus(200)
          resp.setContentType("text/html")
          resp.getWriter.print(Webpage("/", List("/foo", "/404/bar")).toHtml)
        }
        case "/foo" => {
          resp.setStatus(200)
          resp.setContentType("text/html")
          resp.getWriter.print(Webpage("/foo", List("/")).toHtml)
        }
        case "/404/bar" => {
          // no Location header!
          resp.setStatus(302)
          //resp.setHeader("Location", "/foo")
        }
      }
    }
  }

  val servers = Seq(Webserver(9001, servlet))
  
  "test with invalid redirects -- no Location header" in {

    val job = TestData.job

    val runningJob = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status

    val nextHttpResponse: Iteratee[RunEvent, HttpResponse] =
      waitFor[RunEvent] { case ResourceResponseEvent(_, _, _, rr: HttpResponse, _) => rr }

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      hr1 <- nextHttpResponse
      hr2 <- nextHttpResponse
      //hr3 <- nextHttpResponse
    } yield Try {
      hr1.url should be(URL("http://localhost:9001/"))
      hr1.status should be(200)
    }

    (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> test()).getOrFail().get

  }

}
