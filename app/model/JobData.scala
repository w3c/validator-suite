package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime
import org.w3.vs.run._

case class JobData(
    state: RunState,
    resources: Int,
    oks: Int,
    errors: Int,
    warnings: Int
)

object JobData {
  
  val Default = JobData(
    state = null,
    resources = 0,
    oks = 0,
    errors = 0,
    warnings = 0)
  
}
