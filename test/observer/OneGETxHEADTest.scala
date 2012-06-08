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
import play.api.libs.iteratee._

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
    // check that there are 1 + 10 resources that were searched
    //                      10 resources for authority localhost:9002

//    Thread.sleep(3000)
    // pourquoi je ne recois rien ici???????
//    job.listen(testActor)
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

