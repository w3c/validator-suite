package org.w3.vs.model


sealed trait MessageProvenance
case object FromAll extends MessageProvenance
case class FromUser(userId: UserId) extends MessageProvenance
case class FromJob(jobId: JobId) extends MessageProvenance
case class FromRun(runId: RunId) extends MessageProvenance
