package org.w3.vs.model

sealed trait JobDataStatus

case class JobDataRunning(progress: Int) extends JobDataStatus

case object JobDataIdle extends JobDataStatus
