package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.run._
import org.w3.util._
import scalaz.Validation
import org.joda.time.DateTime

trait Store {
  
  def putAssertorResult(result: AssertorResult): Validation[Throwable, Unit]
  
  def putResourceInfo(resourceInfo: ResourceInfo): Validation[Throwable, Unit]
  
  def putJob(job: JobConfiguration): Validation[Throwable, Unit]
  
  def removeJob(jobId: JobConfiguration#Id): Validation[Throwable, Unit]
  
  def getJobById(id: JobConfiguration#Id): Validation[Throwable, Option[JobConfiguration]]
  
  def listJobs(organizationId: Organization#Id): Validation[Throwable, Iterable[JobConfiguration]]
  
  def getResourceInfo(url: URL, jobId: JobConfiguration#Id): Validation[Throwable, ResourceInfo]
  
  def distance(url: URL, jobId: JobConfiguration#Id): Validation[Throwable, Int]
  
  def listResourceInfos(jobId: JobConfiguration#Id, after: Option[DateTime] = None): Validation[Throwable, Iterable[ResourceInfo]]
  
  // this is not really safe (goes through the entire collection)
  // def listAllResourceInfos(): Validation[Throwable, Iterable[ResourceInfo]]
  
  def listAssertorResults(jobId: JobConfiguration#Id, after: Option[DateTime] = None): Validation[Throwable, Iterable[AssertorResult]]
  
  def saveUser(user: User): Validation[Throwable, Unit]
  
  def getUserByEmail(email: String): Validation[Throwable, Option[User]]
  
  def authenticate(email: String, password: String): Validation[Throwable, Option[User]]
  
  def putSnapshot(snapshot: RunSnapshot): Validation[Throwable, Unit]
  
  def latestSnapshotFor(jobId: JobConfiguration#Id): Validation[Throwable, Option[RunSnapshot]]
  
}

