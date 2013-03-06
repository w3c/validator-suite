package org.w3.vs.model

sealed trait JobDataStatus

object JobDataRunning {
  val complete: JobDataRunning = JobDataRunning(100)
}

case class JobDataRunning(progress: Int) extends JobDataStatus

case object JobDataIdle extends JobDataStatus
