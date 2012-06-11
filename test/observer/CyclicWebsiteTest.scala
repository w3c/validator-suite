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
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._

class CyclicWebsiteCrawlTest extends RunTestHelper(new DefaultProdConfiguration { }) with TestKitHelper {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing)).noAssertor()
  
  val job = Job(strategy = strategy, creatorId = userTest.id, organizationId = organizationTest.id, name = "@@")

  val circumference = 10
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet))
  
  "test cyclic" in {
    
    (for {
      _ <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).result(1.second)
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val enumerator = organizationTest.enumerator

    // Tom: I couldn't find an elegant way to do what you want in the general case other that what you did. 
    // This is a simplification for this use case
    val result2 =
      enumerator &>
        Enumeratee.collect[RunUpdate]{case a: UpdateData if a.activity == Idle => a} |>>
        Cont[UpdateData, Input[UpdateData]](in => Done(in,Input.Empty))

    job.run()
    
    //Iteratee.flatten(result2).run.value.get must be /*(an El[RunUpdate] with idle activity)*/

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

    val run = job.getRun().result(1.second) getOrElse sys.error("getRun")

    val rrs = ResourceResponse.getForRun(run.id).result(1.second) getOrElse sys.error("getForRun")

    rrs must have size (circumference + 1)

  }
  
}
