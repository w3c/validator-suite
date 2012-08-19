package org.w3.vs.actor.message

import org.w3.vs.model._
import org.joda.time._

/**
 * An update that happened on an Observation.
 * 
 * Listening to updates lets you know everything about an Observation.
 */
sealed trait RunUpdate 

case class UpdateData(data: JobData, jobId: JobId, activity: RunActivity) extends RunUpdate

/**
 * A new Response was received during the exploration
 */
case class NewResource(context: (OrganizationId, JobId, RunId), resource: ResourceResponse) extends RunUpdate

case class NewAssertorResult(result: AssertorResult, timestamp: DateTime) extends RunUpdate
