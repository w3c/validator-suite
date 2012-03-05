package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.run._
import org.w3.util._
import scala.collection.JavaConverters._
import scala.collection.mutable.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import scalaz._
import Scalaz._
import Validation._

class MemoryStore extends Store {
  
  val results: ConcurrentMap[AssertorResult#Id, AssertorResult] = new ConcurrentHashMap[AssertorResult#Id, AssertorResult]().asScala
    
  val resourceInfos: ConcurrentMap[ResourceInfo#Id, ResourceInfo] = new ConcurrentHashMap[ResourceInfo#Id, ResourceInfo]().asScala
  
  val users: ConcurrentMap[User#Id, User] = new ConcurrentHashMap[User#Id, User]().asScala
  
  val jobs: ConcurrentMap[Job#Id, Job] = new ConcurrentHashMap[Job#Id, Job]().asScala
  
  val snapshots: ConcurrentMap[Job#Id, RunSnapshot] = new ConcurrentHashMap[Job#Id, RunSnapshot]().asScala
  
  def init(): Validation[Throwable, Unit] = Success()
  
  def putAssertorResult(result: AssertorResult): Validation[Throwable, Unit] = fromTryCatch {
    results += result.id -> result
  }

  def putResourceInfo(resourceInfo: ResourceInfo): Validation[Throwable, Unit] = fromTryCatch {
    resourceInfos += resourceInfo.id -> resourceInfo
  }
  
  def putJob(job: Job): Validation[Throwable, Unit] = fromTryCatch {
    jobs += job.id -> job
  }
  
  def getJobById(id: Job#Id) = jobs.get(id) match {
    case Some(job) => Success(job)
    case _ => Failure(new Throwable) // TODO
  }
  
  def getResourceInfo(url: URL, jobId: Job#Id): Validation[Throwable, ResourceInfo] = {
    val riOpt = resourceInfos collectFirst { case (_, ri) if ri.url == url && ri.jobId == jobId => ri }
    riOpt toSuccess (new Throwable("job %s: couldn't find %s" format (jobId.toString, url.toString)))
  }
  
  def distance(url: URL, jobId: Job#Id): Validation[Throwable, Int] = {
    getResourceInfo(url, jobId) map { _.distancefromSeed }
  }
  
  def listResourceInfos(jobId: Job#Id): Validation[Throwable, Iterable[ResourceInfo]] = fromTryCatch {
    resourceInfos.values filter { _.jobId == jobId }
  }
  
  def listAllResourceInfos(): Validation[Throwable, Iterable[ResourceInfo]] = fromTryCatch {
    resourceInfos.values
  }
  
  def listAssertorResults(jobId: Job#Id): Validation[Throwable, Iterable[AssertorResult]] = fromTryCatch {
    results.values filter { _.jobId == jobId }
  }
  
  def saveUser(user: User): Validation[Throwable, Unit] = fromTryCatch {
    users += user.id -> user
  }
  
  def getUserByEmail(email: String): Validation[Throwable, Option[User]] = fromTryCatch {
    users collectFirst { case (_, user) if user.email == email => user }
  }
  
  def authenticate(email: String, password: String): Validation[Throwable, Option[User]] = fromTryCatch {
    users collectFirst { case (_, user) if user.email == email && user.password == password => user }
  }
  
  def putSnapshot(snapshot: RunSnapshot): Validation[Throwable, Unit] = fromTryCatch {
    snapshots += snapshot.jobId -> snapshot
  }
  
  def latestSnapshotFor(jobId: Job#Id): Validation[Throwable, Option[RunSnapshot]] = fromTryCatch {
    import org.joda.time.DateTime
    implicit val timestampOrdering = scala.math.Ordering.fromLessThan((x: DateTime, y: DateTime) => x isBefore y)
    def latest = snapshots.filterKeys{ _ == jobId }.values.maxBy(_.createdAt)
    try Some(latest) catch { case t => None }
  }
    
}
