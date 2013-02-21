package org.w3.vs.model

import org.joda.time._

/**
 * An update that happened on an Observation.
 * 
 * Listening to updates lets you know everything about an Observation.
 */
sealed trait RunUpdate {
  def context: Run.Context
  def data: RunData
}

//case class UpdateData(userId: UserId, jobId: JobId, runId: RunId, data: RunData) extends RunUpdate
case class RunCompleted(context: Run.Context,  data: RunData, completedOn: DateTime) extends RunUpdate
case class RunCancelled(context: Run.Context,  data: RunData) extends RunUpdate

/**
 * A new Response was received during the exploration
 */
case class NewResource(context: Run.Context, resource: ResourceResponse, data: RunData) extends RunUpdate
case class NewAssertorResult(context: Run.Context, result: AssertorResult, run: Run, timestamp: DateTime, data: RunData) extends RunUpdate

sealed trait MessageProvenance
case class FromUser(userId: UserId) extends MessageProvenance
case class FromJob(jobId: JobId) extends MessageProvenance
case class FromRun(runId: RunId) extends MessageProvenance
