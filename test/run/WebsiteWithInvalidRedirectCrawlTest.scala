package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.util.Util._
import javax.servlet.http._

class WebsiteWithInvalidRedirectCrawlTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)

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
        }
      }
    }
  }

  val servers = Seq(Webserver(9001, servlet))
  
  "test with invalid redirects -- no Location header" in {
    
    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    runningJob.listen(testActor)

    fishForMessagePF(3.seconds) {
      case _: RunCompleted => {
        val rrs = ResourceResponse.getFor(runId).getOrFail()
        rrs must have size(3)
        val codes = rrs.toList.collect{ case HttpResponse(_, _, code, _, _, _) => code }
        codes.count(_ == 200) must be(2)
        codes.count(_ == 302) must be(1)
      }
    }

  }
  
}
