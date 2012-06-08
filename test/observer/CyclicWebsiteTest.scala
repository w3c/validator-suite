package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import akka.testkit.TestKit
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import play.api.libs.iteratee._

class CyclicWebsiteCrawlTest extends RunTestHelper(new DefaultProdConfiguration { }) with TestKitHelper {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing)).noAssertor()
  
  val job = Job(strategy = strategy, creatorId = userTest.id, organizationId = organizationTest.id, name = "@@")
  
  val servers = Seq(Webserver(9001, Website.cyclic(10).toServlet))
  
  "test cyclic(10)" in {
    
    (for {
      _ <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).result(1.second)
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val enumerator = organizationTest.enumerator

    job.run()

    // just wait for Idle
    // there must be a better style, or we may have to write some helper functions
    val result =
      enumerator &>
//        Enumeratee.map[RunUpdate](ru => { println("yeah! "+ru); ru }) ><>
        Enumeratee.filter[RunUpdate]{ case UpdateData(_, activity) => activity == Idle ; case _ => false } ><>
        Enumeratee.take(1) ><>
        Enumeratee.map(List(_)) |>>
        Iteratee.consume()

    // the effective wait
    result.flatMap(_.run).value.get

    // TODO

    //job.listen(testActor)
    /*fishForMessagePF(3.seconds) {
      case UpdateData(jobData) if jobData.activity == Idle => {
        val resources = store.listResourceInfos(job.id).waitResult
        resources must have size 11
      }
    }*/
  }
  
}
