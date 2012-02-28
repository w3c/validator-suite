package org.w3.vs.model

import org.w3.vs.observer.Observer
import org.w3.vs.observer._
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
  
  def owns(job: Job): Boolean =
    jobs.contains(job)
  
  def canAccess(job: Job): Boolean = 
    true
    
  def getJobById(id: Job#Id): Option[Job] =
    jobs.find {_.id == id}
  
}
