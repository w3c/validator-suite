package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.util.Util._
import javax.servlet.http._
import play.api.libs.iteratee._
import scala.util.Try

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

    val nextHttpResponse: Iteratee[RunEvent, HttpResponse] =
      waitFor[RunEvent] { case ResourceResponseEvent(_, _, _, rr: HttpResponse, _) => rr }

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      hr1 <- nextHttpResponse
      hr2 <- nextHttpResponse
      hr3 <- nextHttpResponse
    } yield Try {
      hr1.url must be(URL("http://localhost:9001/"))
      hr1.status must be(200)
    }

    (runningJob.enumerator() |>>> test()).getOrFail().get

  }

//  List(CreateRunEvent(513507d68b53af0100aa40b0,513507d68b53af0100aa40b1,513507d78b53af1d00aa40b2,akka://vs/user/runs/f5ce2166-c923-429b-b891-3802b9f25cf5,Strategy(http://localhost:9001/,true,100,Filter(Everything,Nothing),Map()),2013-03-04T20:45:10.157Z,2013-03-04T20:45:11.906Z), ResourceResponseEvent(513507d68b53af0100aa40b0,513507d68b53af0100aa40b1,513507d78b53af1d00aa40b2,HttpResponse(http://localhost:9001/foo,GET,200,Map(Content-Length -> List(131), Content-Type -> List(text/html;charset=ISO-8859-1), Server -> List(Jetty(8.y.z-SNAPSHOT))),List(http://localhost:9001/),Some(Doctype(html,,))),2013-03-04T20:45:13.291Z), ResourceResponseEvent(513507d68b53af0100aa40b0,513507d68b53af0100aa40b1,513507d78b53af1d00aa40b2,HttpResponse(http://localhost:9001/404/bar,GET,302,Map(Content-Length -> List(0), Server -> List(Jetty(8.y.z-SNAPSHOT))),List(),None),2013-03-04T20:45:13.297Z), ResourceResponseEvent(513507d68b53af0100aa40b0,513507d68b53af0100aa40b1,513507d78b53af1d00aa40b2,HttpResponse(http://localhost:9001/,GET,200,Map(Content-Length -> List(175), Content-Type -> List(text/html;charset=ISO-8859-1), Server -> List(Jetty(8.y.z-SNAPSHOT))),List(http://localhost:9001/foo, http://localhost:9001/404/bar),Some(Doctype(html,,))),2013-03-04T20:45:13.241Z), CompleteRunEvent(513507d68b53af0100aa40b0,513507d68b53af0100aa40b1,513507d78b53af1d00aa40b2,RunData(2,0,0),List(),2013-03-04T20:45:13.300Z))
//

}
