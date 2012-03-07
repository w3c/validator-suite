package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime
import org.w3.vs.run._
import akka.dispatch._
import org.w3.vs.VSConfiguration

case class Job(
  id: Job#Id = UUID.randomUUID,
  strategy: EntryPointStrategy,
  createdAt: DateTime = new DateTime,
  creator: String = "john doe",
  name: String = "myJob") {
  
  type Id = UUID
  
  def shortId: String = id.toString.substring(0, 6)
  
  def withNewId(id: Id) = this.copy(id = id)
  
  def getRun()(implicit conf: VSConfiguration): Run = {
    import conf.runCreator
    runCreator.byJobId(id) getOrElse runCreator.runOf(this)
  }
  
  def getData()(implicit conf: VSConfiguration): Future[JobData] = getRun().jobData()

  def isRunning = true
  
}
