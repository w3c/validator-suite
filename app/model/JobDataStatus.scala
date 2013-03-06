package org.w3.vs.model

sealed trait JobDataStatus

object JobDataRunning

case class JobDataRunning(progress: Int) extends JobDataStatus

case object JobDataIdle extends JobDataStatus
