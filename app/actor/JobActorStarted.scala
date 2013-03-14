package org.w3.vs.actor

import org.w3.vs.model._
import akka.actor.ActorRef

case class JobActorStarted(userId: UserId, jobId: JobId, runId: RunId, jobActorRef: ActorRef)
