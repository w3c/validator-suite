package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import scalaz.Success

/**
 * Server 1 -> Server 2
 * 1 GET       10 HEAD
 */
class RunTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy(
      entrypoint = URL("http://localhost:9001/"),
      linkCheck = true,
      maxResources = 100,
      filter = Filter(include = Everything, exclude = Nothing),
      assertorSelector = AssertorSelector.noAssertor)

  val job = Job(name = "@@", strategy = strategy, creator = userTest.id, organization = organizationTest.id)

  val j = 10

  val servers = Seq(
    Webserver(9001, (Website((1 to j) map {
      i => "/" --> ("http://localhost:9002/" + i)
    }).toServlet)),
    Webserver(9002, Website(Seq()).toServlet)
  )

  "A fresh run" in {
    (for {
      _ <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).result(1.second)

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
    PathAware(http, http.path / "localhost_9002") ! SetSleepTime(0)

    job.getLastCompleted().result(1.second) must be (Success(None))

    // Ticket #22 (https://github.com/w3c/validator-suite/issues/22)
    //job.getActivity().result(1.second) must be (Success(Idle))

    val (orgId, jobId, runId) = job.run().getOrFail(1.second)

    job.getActivity().result(1.second) must be (Success(Running))

    // How do I block until the end of the run?
    //Thread.sleep(2000)
    // How do say that the result must be Some(DateTime) without being specific on the date?
    //job.getLastCompleted().result(1.second) must be (Success(Some(_))) // Ticket #13 (https://github.com/w3c/validator-suite/issues/13)

  }

}

