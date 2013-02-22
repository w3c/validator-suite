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
  def data: RunData
}

//case class UpdateData(userId: UserId, jobId: JobId, runId: RunId, data: RunData) extends RunUpdate
case class RunCompleted(userId: UserId, jobId: JobId, runId: RunId, data: RunData, completedOn: DateTime) extends RunUpdate
case class RunCancelled(userId: UserId, jobId: JobId, runId: RunId, data: RunData) extends RunUpdate

/**
 * A new Response was received during the exploration
 */
case class NewResource(userId: UserId, jobId: JobId, runId: RunId, resource: ResourceResponse, data: RunData) extends RunUpdate
case class NewAssertorResult(userId: UserId, jobId: JobId, runId: RunId, result: AssertorResult, run: Run, timestamp: DateTime, data: RunData) extends RunUpdate

sealed trait MessageProvenance
case object FromAll extends MessageProvenance
case class FromUser(userId: UserId) extends MessageProvenance
case class FromJob(jobId: JobId) extends MessageProvenance
case class FromRun(runId: RunId) extends MessageProvenance
