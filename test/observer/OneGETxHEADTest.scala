package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.specs2.mutable.Specification
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor._
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
  
  val job = Job(strategy = strategy, creatorId = userTest.id, organizationId = organizationTest.id, name = "@@")
  
  val servers = Seq(
      unfiltered.jetty.Http(9001).filter(Website((1 to j) map { i => "/" --> ("http://localhost:9002/"+i) }).toPlanify),
      unfiltered.jetty.Http(9002).filter(Website(Seq()).toPlanify)
  )

  "test OneGETxHEAD" in {
    //stores.OrganizationStore.put(organizationTest)
    //store.putJob(job).waitResult()
    (for {
      a <- Organization.save(organizationTest)
      b <- Job.save(job)
    } yield ()).await(5 seconds)
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
    PathAware(http, http.path / "localhost_9002") ! SetSleepTime(0)
    job.run()
    job.listen(testActor)
    /*fishForMessagePF(3.seconds) {
      case UpdateData(jobData) if jobData.activity == Idle => {
        def ris = store.listResourceInfos(job.id).waitResult()
        ris must have size 11
        val urls8081 = ris filter { _.url.authority == "localhost:9002" }
        urls8081 must have size(j)
      }
    }*/
  }

}

