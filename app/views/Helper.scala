package views

import org.w3.vs.model.Job
import org.w3.vs.prod._
import akka.dispatch.Await
import akka.util.duration._
import org.w3.vs.run.Stopped
import akka.pattern.AskTimeoutException
import org.w3.vs.run.NotYetStarted

object Helper {
  
  // moved to job
  def getJobStatus(id: Job#Id): org.w3.vs.run.RunStatus = {
    configuration.runCreator.byJobId(id).fold(
      failure => NotYetStarted,
      success => 
        try {
          Await.result(success.status, 100 milliseconds)
        } catch {
          case e: AskTimeoutException => {println(e); Stopped}
        }
    )
  }
  
}