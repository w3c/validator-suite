package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime
import org.w3.vs.run._
import akka.dispatch._
import org.w3.vs.VSConfiguration

object Job {
  
  def fake(strategy: EntryPointStrategy): Job = {
    val fakeUser = User.fake
    Job(name = "fake job", creator = fakeUser.id, organization = fakeUser.organization, strategy = strategy)
  }
  
}

// I guess the case class is actually just the configuration part
// we should have a wrapper for that guy that would act as a facade for the JobActor
// this is now called JobLive, but that sounds wrong
case class Job(
  id: Job#Id = UUID.randomUUID,
  strategy: EntryPointStrategy,
  createdAt: DateTime = new DateTime,
  creator: User#Id = null,
  organization: Organization#Id = null,
  name: String) {
  
  type Id = UUID
  
  def shortId: String = id.toString.substring(0, 6)

  // should this be private?
  def jobLive()(implicit conf: VSConfiguration): JobLive = JobLive.getJobLiveOrCreate(id, this)
  
  def refresh()(implicit conf: VSConfiguration) = jobLive().refresh()
  
  def stop()(implicit conf: VSConfiguration) = jobLive().stop()
  
  def getData()(implicit conf: VSConfiguration): Future[JobData] = jobLive().jobData()
  
  // there should be a better way
  def assignTo(user: User): Job = {
    copy(creator = user.id, organization = user.organization)
  }
}
