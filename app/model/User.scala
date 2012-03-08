package org.w3.vs.model

import org.w3.vs.run.Run
import org.w3.vs.run._
import org.w3.vs.model._
import java.util.UUID

case class User(
    id: User#Id = UUID.randomUUID,
    email: String,
    name: String,
    password: String,
    jobs: Map[Job#Id, Job] = Map.empty) {
  
  type Id = UUID
  
  def withJob(job: Job): User =
    this.copy(jobs = jobs + ((job.id, job)))
  
  def owns(job: Job): Boolean =
    jobs.isDefinedAt(job.id)
  
}
