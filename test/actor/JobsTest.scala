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
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:9001",
      entrypoint=URL("http://localhost:9001/"),
      distance=11,
      linkCheck=true,
      maxNumberOfResources = 100,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val jobConf = JobConfiguration.fake(strategy = strategy)

  
  "create and asking for jobs" in {

    // val testF =
    //   for {
    //     // first time: job is created
    //     createdJob <- JobsActor.getJobOrCreate(jobConf)
    //     createdJobData <- createdJob.jobData()
    //     retrievedJob <- JobsActor.getJobOrCreate(jobConf)
    //     //_ <- job.refresh()
    //   } yield {
    //     createdJobData.jobId must be === (jobConf.id)
    //     retrievedJob.configuration.id must be === (jobConf.id)
    //   }

    // Await.result(testF, 5.seconds)

  }
  
  
}
