package org.w3.vs.actor

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

class JobsTest extends RunTestHelper(new DefaultProdConfiguration { }) {
  
  val servers = Seq.empty

  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val job = Job(
      name = "test",
      creatorId = UserId(),
      organizationId = OrganizationId(),
      strategy = strategy)

  
  "create and asking for jobs" in {

    // val testF =
    //   for {
    //     // first time: job is created
    //     createdJob <- JobsActor.getJobOrCreate(job)
    //     createdJobData <- createdJob.jobData()
    //     retrievedJob <- JobsActor.getJobOrCreate(job)
    //     //_ <- job.refresh()
    //   } yield {
    //     createdJobData.jobId must be === (job.id)
    //     retrievedJob.configuration.id must be === (job.id)
    //   }

    // Await.result(testF, 5.seconds)

  }
  
  
}
