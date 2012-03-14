package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime
import org.w3.vs.run._

case class JobData(
    jobId: Job#Id,
    state: RunState,
    resources: Int,
    oks: Int,
    errors: Int,
    warnings: Int
)
