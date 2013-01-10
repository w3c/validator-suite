package org.w3.vs.actor.message

import org.w3.vs.model._
import org.joda.time._

/**
 * An update that happened on an Observation.
 * 
 * Listening to updates lets you know everything about an Observation.
 */
sealed trait RunUpdate 

case class UpdateData(jobId: JobId, data: JobData) extends RunUpdate
case class RunCompleted(jobId: JobId, completedOn: DateTime) extends RunUpdate
case class RunCancelled(jobId: JobId) extends RunUpdate

/**
 * A new Response was received during the exploration
 */
case class NewResource(context: Run.Context, resource: ResourceResponse) extends RunUpdate

case class NewAssertorResult(result: AssertorResult, run: Run, timestamp: DateTime) extends RunUpdate
