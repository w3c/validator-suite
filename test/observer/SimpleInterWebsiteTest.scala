package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import play.api.libs.iteratee._

/**
  * Server 1 -> Server 2
  * 1 GET       1 HEAD
  */
class SimpleInterWebsiteTest extends RunTestHelper(new DefaultProdConfiguration { }) with TestKitHelper {

  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing)).noAssertor()
  
  val job = Job(strategy = strategy, creatorId = userTest.id, organizationId = organizationTest.id, name = "@@")(implicitConfiguration)
  
  val servers = Seq(
      Webserver(9001, Website(Seq("/" --> "http://localhost:9002/")).toServlet),
      Webserver(9002, Website(Seq()).toServlet)
  )

  "test simpleInterWebsite" in {
    (for {
      _ <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).result(1.second)

    val enumerator = organizationTest.enumerator

    job.run()

    job.listen(testActor)

    fishForMessagePF(3.seconds) {
      case UpdateData(_, activity) if activity == Idle => {
        val rrs = ResourceResponse.getForJob(job).result(1.second) getOrElse sys.error("getForRun")
        rrs must have size (2)
      }
    }

  }
  
}
