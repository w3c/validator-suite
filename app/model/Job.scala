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

case class Job(
  id: Job#Id = UUID.randomUUID,
  strategy: EntryPointStrategy,
  createdAt: DateTime = new DateTime,
  creator: User#Id = null,
  organization: Organization#Id = null,
  name: String) {
  
  type Id = UUID
  
  def shortId: String = id.toString.substring(0, 6)
  
  def getRun()(implicit conf: VSConfiguration): Run = {
    import conf.runCreator
    runCreator.byJobId(id) getOrElse runCreator.runOf(this)
  }
  
  def run()(implicit conf: VSConfiguration) = getRun().run()
  
  def runNow()(implicit conf: VSConfiguration) = getRun().runNow()
  
  def stop()(implicit conf: VSConfiguration) = getRun().stop()
  
  def getData()(implicit conf: VSConfiguration): Future[JobData] = getRun().jobData()
  
  def assignTo(user: User): Job = {
    copy(creator = user.id, organization = user.organization)
  }
}
