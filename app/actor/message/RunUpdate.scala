package org.w3.vs.model

import org.joda.time._

/**
 * An update that happened on an Observation.
 * 
 * Listening to updates lets you know everything about an Observation.
 */
sealed trait RunUpdate {
  def userId: UserId
  def jobId: JobId
  def runId: RunId
}

case class UpdateData(userId: UserId, jobId: JobId, runId: RunId, data: JobData) extends RunUpdate
case class RunCompleted(userId: UserId, jobId: JobId, runId: RunId, completedOn: DateTime) extends RunUpdate
case class RunCancelled(userId: UserId, jobId: JobId, runId: RunId) extends RunUpdate

/**
 * A new Response was received during the exploration
 */
case class NewResource(context: Run.Context, resource: ResourceResponse) extends RunUpdate {
  val userId: UserId = context._1
  val jobId: JobId = context._2
  val runId: RunId = context._3
}

case class NewAssertorResult(result: AssertorResult, run: Run, timestamp: DateTime) extends RunUpdate {
  import result.context
  val userId: UserId = context._1
  val jobId: JobId = context._2
  val runId: RunId = context._3
}





sealed trait MessageProvenance
case class FromUser(userId: UserId) extends MessageProvenance
case class FromJob(jobId: JobId) extends MessageProvenance
case class FromRun(runId: RunId) extends MessageProvenance
