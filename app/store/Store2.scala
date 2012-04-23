/*package org.w3.vs.store

import org.w3.vs.actor._
import org.w3.vs.model._
import org.w3.vs.exception._
import org.w3.util._
import org.w3.util.Pimps._
import org.joda.time.DateTime

trait Store2 {
  
  
  
  // Organization
  def getOrganizations(
      id: Option[JobId] = None,
      url: Option[String] = None,
      name: Option[String] = None,
      address: Option[String] = None,
      domainNames: Option[Iterable[Authority]] = None,
      admin: Option[UserId] = None,
      createdOn: Option[DateTime] = None): FutureVal[Exception, Iterable[Organization]]
  
  def createOrganization(
      id: JobId,
      url: String,
      name: String,
      address: String,
      domainNames: Iterable[Authority],
      admin: UserId,
      createdOn: DateTime): FutureVal[Exception, Organization]
  
  def updateOrganization(
      id: Option[JobId] = None,
      url: Option[String] = None,
      name: Option[String] = None,
      address: Option[String] = None,
      domainNames: Option[Iterable[Authority]] = None,
      admin: Option[UserId] = None,
      createdOn: Option[DateTime] = None): FutureVal[Exception, Organization]
  
  def deleteOrganization(
      id: Option[JobId] = None,
      url: Option[String] = None,
      name: Option[String] = None,
      address: Option[String] = None,
      domainNames: Option[Iterable[Authority]] = None,
      admin: Option[UserId] = None,
      createdOn: Option[DateTime] = None): FutureVal[Exception, Unit]
  
  
  
  // User
  def getUsers(
      id: Option[JobId] = None,
      organizationId: Option[OrganizationId],
      email: Option[String] = None,
      password: Option[String] = None,
      createdOn: Option[DateTime] = None): FutureVal[Exception, Iterable[User]]
  
  def createUser(
      id: JobId,
      organizationId: OrganizationId,
      email: String, 
      password: String,
      createdOn: DateTime): FutureVal[Exception, User]
  
  // failure: NotFoundUserException
  def updateUser(
      id: Option[JobId] = None,
      organizationId: Option[OrganizationId],
      email: Option[String] = None, 
      password: Option[String] = None,
      createdOn: Option[DateTime] = None): FutureVal[Exception, User]
  
  // failure: NotFoundUserException
  def deleteUser(
      id: Option[JobId] = None,
      organizationId: Option[OrganizationId],
      email: Option[String] = None, 
      password: Option[String] = None,
      createdOn: Option[DateTime] = None): FutureVal[Exception, Unit]
  
  
  
  // Job
  def getJobs(
    id: Option[JobId] = None,
    name: Option[String] = None,
    creatorId: Option[UserId] = None,
    organizationId: Option[OrganizationId] = None,
    strategyId: Option[StrategyId] = None,
    lastCompleted: Option[Option[DateTime]] = None,
    createdOn: Option[DateTime] = None): FutureVal[Exception, Iterable[Job]]
  
  def createJob(
    id: JobId,
    name: String,
    creatorId: UserId,
    organizationId: OrganizationId,
    strategy: Strategy,
    lastCompleted: Option[DateTime],
    createdOn: DateTime): FutureVal[Exception, Job]
  
  def updateJob(
    id: Option[JobId] = None,
    name: Option[String] = None,
    creatorId: Option[UserId] = None,
    organizationId: Option[OrganizationId] = None,
    strategy: Option[Strategy] = None,
    lastCompleted: Option[Option[DateTime]] = None,
    createdOn: Option[DateTime] = None): FutureVal[Exception, Job]
  
  def deleteJob(
    id: Option[JobId] = None,
    name: Option[String] = None,
    creatorId: Option[UserId] = None,
    organizationId: Option[OrganizationId] = None,
    strategy: Option[Strategy] = None,
    lastCompleted: Option[Option[DateTime]] = None,
    createdOn: Option[DateTime] = None): FutureVal[Exception, Unit]
  
  
  
  // JobData
  def getJobDatas(
    jobId: Option[JobId] = None,
    runId: Option[RunId] = None,
    resources: Option[Int] = None,
    errors: Option[Int] = None,
    warnings: Option[Int] = None,
    date: Option[DateTime] = None): FutureVal[Exception, Iterable[JobData]]
  
  def createJobData(
    jobId: JobId,
    runId: RunId,
    resources: Int,
    errors: Int,
    warnings: Int,
    date: DateTime): FutureVal[Exception, JobData]
  
  def updateJobData(
    jobId: Option[JobId] = None,
    runId: Option[RunId] = None,
    resources: Option[Int] = None,
    errors: Option[Int] = None,
    warnings: Option[Int] = None,
    date: Option[DateTime] = None): FutureVal[Exception, JobData]
  
  def deleteJobData(
    jobId: Option[JobId] = None,
    runId: Option[RunId] = None,
    resources: Option[Int] = None,
    errors: Option[Int] = None,
    warnings: Option[Int] = None,
    date: Option[DateTime] = None): FutureVal[Exception, Unit]
  
  
  
  // Strategy
  def getStrategies(
    id: Option[StrategyId] = None,
    entrypoint: Option[URL] = None,
    distance: Option[Int] = None,
    linkCheck: Option[Boolean] = None,
    maxNumberOfResources: Option[Int] = None, 
    filter: Iterable[(String, String)] = Iterable.empty): FutureVal[Exception, Iterable[Strategy]]
  
  def createStrategy(
    id: StrategyId,
    entrypoint: URL,
    distance: Int,
    linkCheck: Boolean,
    maxNumberOfResources: Int, 
    filter: Iterable[(String, String)]): FutureVal[Exception, Strategy]
  
  def updateStrategy(
    id: Option[StrategyId] = None,
    entrypoint: Option[URL] = None,
    distance: Option[Int] = None,
    linkCheck: Option[Boolean] = None,
    maxNumberOfResources: Option[Int] = None, 
    filter: Iterable[(String, String)] = Iterable.empty): FutureVal[Exception, Strategy]
  
  def deleteStrategy(
    id: Option[StrategyId] = None,
    entrypoint: Option[URL] = None,
    distance: Option[Int] = None,
    linkCheck: Option[Boolean] = None,
    maxNumberOfResources: Option[Int] = None, 
    filter: Iterable[(String, String)] = Iterable.empty): FutureVal[Exception, Unit]
  
  
  // Resources
  
  // Assertions
  
  // 
  
  def putAssertorResult(result: AssertorResult): FutureVal[SuiteException, Unit]
  
  def putResourceInfo(resourceInfo: ResourceInfo): FutureVal[SuiteException, Unit]
  
  def putJob(job: Job): FutureVal[SuiteException, Unit]
  
  def putJob2(job: Job): FutureVal[SuiteException, Unit]
  
  def removeJob(jobId: JobId): FutureVal[SuiteException, Unit]

  def getJobById(id: JobId): FutureVal[SuiteException, Job]
  
  def getJobById2(id: JobId): FutureVal[SuiteException, Job]
  
  def listJobs(organizationId: OrganizationId): FutureVal[SuiteException, Iterable[Job]]
  
  def listJobs2(organizationId: OrganizationId): FutureVal[SuiteException, Iterable[Job]]
  
  def putOrganization(organizationData: OrganizationData): FutureVal[SuiteException, Unit]
  
  def removeOrganization(organizationId: OrganizationId): FutureVal[SuiteException, Unit]

  def getOrganizationDataById(id: OrganizationId): FutureVal[SuiteException, OrganizationData]
  
  def getResourceInfo(url: URL, jobId: JobId): FutureVal[SuiteException, ResourceInfo]
  
//  def distance(url: URL, jobId: JobId): FutureVal[SuiteException, Int]
  
  def listResourceInfos(jobId: JobId, after: Option[DateTime] = None): FutureVal[SuiteException, Iterable[ResourceInfo]]

  def listResourceInfosByRunId(runId: RunId, after: Option[DateTime] = None): FutureVal[SuiteException, Iterable[ResourceInfo]]
  
  // this is not really safe (goes through the entire collection)
  // def listAllResourceInfos(): FutureVal[SuiteException, Iterable[ResourceInfo]]
  
  def listAssertorResults(jobId: JobId, after: Option[DateTime] = None): FutureVal[SuiteException, Iterable[AssertorResult]]
  
  def saveUser(user: User): FutureVal[SuiteException, Unit]
  
  def getUserByEmail(email: String): FutureVal[SuiteException, User]
  
  def getUserByEmail2(email: String): FutureVal[SuiteException, User]
  
  def authenticate(email: String, password: String): FutureVal[SuiteException, User]
  
  def putSnapshot(snapshot: RunSnapshot): FutureVal[SuiteException, Unit]
  
  def latestSnapshotFor(jobId: JobId): FutureVal[SuiteException, Option[RunSnapshot]]
  
}

*/