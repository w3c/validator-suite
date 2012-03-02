package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.run._
import org.w3.util._
import scalaz.Validation

trait Store {
  
  def putAssertorResult(result: AssertorResult): Validation[Throwable, Unit]
  
  def putResourceInfo(resourceInfo: ResourceInfo): Validation[Throwable, Unit]
  
  def putJob(job: Job): Validation[Throwable, Unit]
  
  def getJobById(id: Job#Id): Validation[Throwable, Job]
  
  def getResourceInfo(url: URL, jobId: Job#Id): Validation[Throwable, ResourceInfo]
  
  def distance(url: URL, jobId: Job#Id): Validation[Throwable, Int]
  
  def listResourceInfos(jobId: Job#Id): Validation[Throwable, Iterable[ResourceInfo]]
  
  // this is not really safe (goes through the entire collection)
  // def listAllResourceInfos(): Validation[Throwable, Iterable[ResourceInfo]]
  
  def listAssertorResults(jobId: Job#Id): Validation[Throwable, Iterable[AssertorResult]]
  
  def saveUser(user: User): Validation[Throwable, Unit]
  
  def getUserByEmail(email: String): Validation[Throwable, Option[User]]
  
  def authenticate(email: String, password: String): Validation[Throwable, Option[User]]
  
  
}

