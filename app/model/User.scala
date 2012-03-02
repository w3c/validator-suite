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
    jobs: List[Job] = List.empty) {
  
  type Id = UUID
  
  def withJob(job: Job): User =
    this.copy(jobs = jobs :+ job)
  
  def withoutJob(job: Job): User =
    this.copy(jobs = jobs.filterNot(_ == job)) // XXX Fixme? 
    
  def owns(job: Job): Boolean =
    jobs.contains(job)
    
  def getJobById(id: Job#Id): Option[Job] =
    jobs.find {_.id == id}
  
}
