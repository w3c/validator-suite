package org.w3.vs.model

import java.net.URL
import org.joda.time.DateTime

case class JobData (
  id: JobId,
  name: String,
  entrypoint: URL,
  status: JobDataStatus,
  completedOn: Option[DateTime],
  warnings: Int,
  errors: Int,
  resources: Int,
  maxResources: Int,
  health: Int
)

sealed trait JobDataStatus

case object JobDataRunning extends JobDataStatus
case object JobDataIdle extends JobDataStatus