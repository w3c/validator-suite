package org.w3.vs.store

import MemoryStore.{ fromTryCatch, fromTryCatchV }
import java.util.concurrent.ConcurrentHashMap
import org.joda.time.DateTime
import org.w3.util.{ FutureValidationNoTimeOut, URL }
import org.w3.util.DateTimeOrdering
import org.w3.util.FutureValidation.{ delayedValidation, immediateValidation }
import org.w3.vs.VSConfiguration
import org.w3.vs.exception._
import org.w3.vs.model._
import scala.collection.JavaConverters.asScalaConcurrentMapConverter
import scala.collection.mutable.ConcurrentMap
import scalaz._
import scalaz.Scalaz._

object MemoryStore {

  def fromTryCatch[T](body: => T)(implicit context: akka.dispatch.ExecutionContext): FutureValidationNoTimeOut[SuiteException, T] =
    fromTryCatchV(Success(body))

  def fromTryCatchV[T](body: => Validation[SuiteException, T])(implicit context: akka.dispatch.ExecutionContext): FutureValidationNoTimeOut[SuiteException, T] = immediateValidation {
    try {
      body
    } catch {
      case t =>
        Failure(StoreException(t))
    }
  }

}

class MemoryStore()(implicit configuration: VSConfiguration) extends Store {

  implicit val context = configuration.storeExecutionContext

  val results: ConcurrentMap[AssertorResult#Id, AssertorResult] = new ConcurrentHashMap[AssertorResult#Id, AssertorResult]().asScala

  val resourceInfos: ConcurrentMap[ResourceInfo#Id, ResourceInfo] = new ConcurrentHashMap[ResourceInfo#Id, ResourceInfo]().asScala

  val users: ConcurrentMap[UserId, User] = new ConcurrentHashMap[UserId, User]().asScala

  val jobs: ConcurrentMap[JobId, Job] = new ConcurrentHashMap[JobId, Job]().asScala

  val organizations: ConcurrentMap[OrganizationId, OrganizationData] = new ConcurrentHashMap[OrganizationId, OrganizationData]().asScala

  val snapshots: ConcurrentMap[JobId, RunSnapshot] = new ConcurrentHashMap[JobId, RunSnapshot]().asScala

  def init(): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {}

  def putAssertorResult(result: AssertorResult): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {
    results += result.id -> result
  }

  def putResourceInfo(resourceInfo: ResourceInfo): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {
    resourceInfos += resourceInfo.id -> resourceInfo
  }

  def putJob(job: Job): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {
    jobs += job.id -> job
  }

  def removeJob(jobId: JobId): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {
    jobs -= jobId
  }

  def getJobById(id: JobId): FutureValidationNoTimeOut[SuiteException, Job] = fromTryCatchV {
    jobs.get(id) toSuccess (UnknownJob)
  }

  def listJobs(organizationId: OrganizationId): FutureValidationNoTimeOut[SuiteException, Iterable[Job]] = fromTryCatch {
    jobs collect { case (_, job) if organizationId === job.organizationId => job }
  }

  /* organizations */

  def putOrganization(organizationData: OrganizationData): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {
    organizations += organizationData.id -> organizationData
  }

  def removeOrganization(organizationId: OrganizationId): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {
    organizations -= organizationId
  }

  def getOrganizationDataById(id: OrganizationId): FutureValidationNoTimeOut[SuiteException, OrganizationData] = fromTryCatchV {
    organizations.get(id) toSuccess (Unknown)
  }

  def getResourceInfo(url: URL, jobId: JobId): FutureValidationNoTimeOut[SuiteException, ResourceInfo] = delayedValidation {
    resourceInfos collectFirst { case (_, ri) if ri.url == url && ri.jobId == jobId => ri } toSuccess (UnknownJob)
  }

  def distance(url: URL, jobId: JobId): FutureValidationNoTimeOut[SuiteException, Int] = {
    //getResourceInfo(url, jobId) map { _.distancefromSeed }
    sys.error("don't think this is actually needed for v1")
  }

  def listResourceInfos(jobId: JobId, after: Option[DateTime] = None): FutureValidationNoTimeOut[SuiteException, Iterable[ResourceInfo]] = fromTryCatch {
    after match {
      case None => resourceInfos.values filter { _.jobId === jobId }
      case Some(date) => resourceInfos.values.filter { ri => ri.jobId === jobId && ri.timestamp.isAfter(date) }.toSeq.sortBy(_.timestamp)
    }
  }

  def listResourceInfosByRunId(runId: RunId, after: Option[DateTime] = None): FutureValidationNoTimeOut[SuiteException, Iterable[ResourceInfo]] = fromTryCatch {
    after match {
      case None => resourceInfos.values filter { _.runId === runId }
      case Some(date) => resourceInfos.values.filter { ri => ri.runId === runId && ri.timestamp.isAfter(date) }.toSeq.sortBy(_.timestamp)
    }
  }

  def listAssertorResults(jobId: JobId, after: Option[DateTime] = None): FutureValidationNoTimeOut[SuiteException, Iterable[AssertorResult]] = fromTryCatch {
    after match {
      case None => results.values filter { _.jobId == jobId }
      case Some(date) => results.values.filter { r => r.jobId == jobId && r.timestamp.isAfter(date) }.toSeq.sortBy(_.timestamp)
    }
  }

  def saveUser(user: User): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {
    users += user.id -> user
  }

  def getUserByEmail(email: String): FutureValidationNoTimeOut[SuiteException, User] = fromTryCatchV {
    users collectFirst { case (_, user) if user.email === email => user } toSuccess (UnknownUser)
  }

  def authenticate(email: String, password: String): FutureValidationNoTimeOut[SuiteException, User] = fromTryCatchV {
    users collectFirst { case (_, user) if user.email === email && user.password === password => user } toSuccess (UnknownUser)
  }

  def putSnapshot(snapshot: RunSnapshot): FutureValidationNoTimeOut[SuiteException, Unit] = fromTryCatch {
    snapshots += snapshot.jobId -> snapshot
  }

  def latestSnapshotFor(jobId: JobId): FutureValidationNoTimeOut[SuiteException, Option[RunSnapshot]] = fromTryCatch {
    def latest = snapshots.filterKeys { _ == jobId }.values.maxBy(_.createdAt)(DateTimeOrdering)
    try Some(latest) catch { case t => None }
  }

}
