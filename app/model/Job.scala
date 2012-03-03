package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime
import org.w3.vs.prod._
import org.w3.vs.run._
import akka.dispatch.Await
import akka.pattern.AskTimeoutException
import akka.util.duration._

case class Job(
  id: Job#Id = UUID.randomUUID,
  strategy: EntryPointStrategy,
  createdAt: DateTime = new DateTime,
  creator: String = "john doe",
  name: String = "myJob") {
  
  type Id = UUID
  
  def shortId: String = id.toString.substring(0, 6)
  
  def withNewId(id: Id) = this.copy(id = id)
  
  def getStatus: RunStatus = {
    configuration.runCreator.byJobId(id).fold(
      failure => NotYetStarted, // What should the status be when no run exists for the job ?
      success => 
        try {
          Await.result(success.status, 100 milliseconds)
        } catch {
          case e: AskTimeoutException => {println(e); Stopped}
          // When over, a run does not return a Stopped or Idle RunStatus but just fails to produce one.
        }
    )
  }
  
  def isRunning: Boolean = {
    configuration.runCreator.byJobId(id).isSuccess
  }
  
}
