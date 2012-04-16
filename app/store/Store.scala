package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.actor._
import org.w3.util._
import org.w3.util.Pimps._
import org.joda.time.DateTime
import org.w3.vs.exception._

trait Store {
  
  def putAssertorResult(result: AssertorResult): FutureValidationNoTimeOut[SuiteException, Unit]
  
  def putResourceInfo(resourceInfo: ResourceInfo): FutureValidationNoTimeOut[SuiteException, Unit]
  
  def putJob(job: Job): FutureValidationNoTimeOut[SuiteException, Unit]
  
  def removeJob(jobId: JobId): FutureValidationNoTimeOut[SuiteException, Unit]

  def getJobById(id: JobId): FutureValidationNoTimeOut[SuiteException, Job]
  
  def listJobs(organizationId: OrganizationId): FutureValidationNoTimeOut[SuiteException, Iterable[Job]]
  
  def putOrganization(organizationData: OrganizationData): FutureValidationNoTimeOut[SuiteException, Unit]
  
  def removeOrganization(organizationId: OrganizationId): FutureValidationNoTimeOut[SuiteException, Unit]

  def getOrganizationDataById(id: OrganizationId): FutureValidationNoTimeOut[SuiteException, OrganizationData]
  
  def getResourceInfo(url: URL, jobId: JobId): FutureValidationNoTimeOut[SuiteException, ResourceInfo]
  
//  def distance(url: URL, jobId: JobId): FutureValidationNoTimeOut[SuiteException, Int]
  
  def listResourceInfos(jobId: JobId, after: Option[DateTime] = None): FutureValidationNoTimeOut[SuiteException, Iterable[ResourceInfo]]

  def listResourceInfosByRunId(runId: RunId, after: Option[DateTime] = None): FutureValidationNoTimeOut[SuiteException, Iterable[ResourceInfo]]
  
  // this is not really safe (goes through the entire collection)
  // def listAllResourceInfos(): FutureValidationNoTimeOut[SuiteException, Iterable[ResourceInfo]]
  
  def listAssertorResults(jobId: JobId, after: Option[DateTime] = None): FutureValidationNoTimeOut[SuiteException, Iterable[AssertorResult]]
  
  def saveUser(user: User): FutureValidationNoTimeOut[SuiteException, Unit]
  
  def getUserByEmail(email: String): FutureValidationNoTimeOut[SuiteException, User]
  
  def authenticate(email: String, password: String): FutureValidationNoTimeOut[SuiteException, User]
  
  def putSnapshot(snapshot: RunSnapshot): FutureValidationNoTimeOut[SuiteException, Unit]
  
  def latestSnapshotFor(jobId: JobId): FutureValidationNoTimeOut[SuiteException, Option[RunSnapshot]]
  
}

