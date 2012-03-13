package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime
import org.w3.vs.run._
import akka.dispatch.Await
import akka.pattern.AskTimeoutException
import akka.util.duration._

case class JobData(
    status: RunStatus,
    resources: Int,
    oks: Int,
    errors: Int,
    warnings: Int
)

object JobData {
  
  val Default = 
    new JobData(
      status = NotYetStarted,
      resources = 0,
      oks = 0,
      errors = 0,
      warnings = 0)
  
}