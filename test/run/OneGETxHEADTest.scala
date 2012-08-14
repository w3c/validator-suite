package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._

/**
  * Server 1 -> Server 2
  * 1 GET       10 HEAD
  */
class OneGETxHEADTest extends RunTestHelper(new DefaultProdConfiguration { }) with TestKitHelper {
  
  val j = 10
  
  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing)).noAssertor()
  
  val job = Job(name = "@@", strategy = strategy, creator = userTest.id, organization = organizationTest.id)
  
  val servers = Seq(
      Webserver(9001, (Website((1 to j) map { i => "/" --> ("http://localhost:9002/"+i) }).toServlet)),
      Webserver(9002, Website(Seq()).toServlet)
  )

  "test OneGETxHEAD" in {
    (for {
      _ <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).result(1.second)
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
    PathAware(http, http.path / "localhost_9002") ! SetSleepTime(0)

    val (orgId, jobId, runId) = job.run().getOrFail(1.second)

    job.listen(testActor)

    fishForMessagePF(3.seconds) {
      case UpdateData(_, _, activity) if activity == Idle => {
        val rrs = ResourceResponse.bananaGetFor(orgId, jobId, runId).getOrFail(3.seconds)
        rrs must have size (j + 1)
        rrs.filter( _.url.authority == "localhost:9002" ) must have size (j)
      }
    }

  }

}

