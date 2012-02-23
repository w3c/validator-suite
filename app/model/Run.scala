package org.w3.vs.model

import java.util.UUID
import org.joda.time._

// the live version of a report
case class Run(
    id: Run#Id = UUID.randomUUID,
    // the job that were used to start this report
    job: Job,
    // when the live report was started
    startedAt: DateTime = new DateTime) {
  
  type Id = UUID
  
  val shortId = id.toString.substring(0, 6)
  
}