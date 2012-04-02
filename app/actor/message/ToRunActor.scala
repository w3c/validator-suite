package org.w3.vs.actor.message

import akka.actor.ActorRef

case object Refresh
case object Stop

case object BeProactive
case object BeLazy

case object GetJobData
case object TellTheWorldYouAreAlive
case object NoMorePendingAssertion

import org.w3.vs.model.{ OrganizationId, JobId }

case class Message(organizationId: OrganizationId, jobId: JobId, msg: Any)
