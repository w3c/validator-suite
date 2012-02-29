package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.observer._
import org.w3.util._
import scala.collection.JavaConverters._
import scala.collection.mutable.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import scalaz._
import Scalaz._
import Validation._

class MemoryStore extends Store {
  
  val assertions: ConcurrentMap[Assertion#Id, Assertion] = new ConcurrentHashMap[Assertion#Id, Assertion]().asScala
    
  val resourceInfos: ConcurrentMap[ResourceInfo#Id, ResourceInfo] = new ConcurrentHashMap[ResourceInfo#Id, ResourceInfo]().asScala
  
  val users: ConcurrentMap[User#Id, User] = new ConcurrentHashMap[User#Id, User]().asScala
  
  val runs: ConcurrentMap[Run#Id, Run] = new ConcurrentHashMap[Run#Id, Run]().asScala
  
  def init(): Validation[Throwable, Unit] = Success()
  
  def putAssertion(assertion: Assertion): Validation[Throwable, Unit] = fromTryCatch {
    assertions += assertion.id -> assertion
  }

  def putResourceInfo(resourceInfo: ResourceInfo): Validation[Throwable, Unit] = fromTryCatch {
    resourceInfos += resourceInfo.id -> resourceInfo
  }
  
  def putRun(run: Run): Validation[Throwable, Unit] = fromTryCatch {
    runs += run.id -> run
  }
  
  def getResourceInfo(url: URL, runId: Run#Id): Validation[Throwable, ResourceInfo] = {
    val riOpt = resourceInfos collectFirst { case (_, ri) if ri.url == url && ri.runId == runId => ri }
    riOpt toSuccess (new Throwable("run %s: couldn't find %s" format (runId.toString, url.toString)))
  }
  
  def distance(url: URL, runId: Run#Id): Validation[Throwable, Int] = {
    getResourceInfo(url, runId) map { _.distancefromSeed }
  }
  
  def listResourceInfos(runId: Run#Id): Validation[Throwable, Iterable[ResourceInfo]] = fromTryCatch {
    resourceInfos.values filter { _.runId == runId }
  }
  
  def listAllResourceInfos(): Validation[Throwable, Iterable[ResourceInfo]] = fromTryCatch {
    resourceInfos.values
  }
  
  def listAssertions(runId: Run#Id): Validation[Throwable, Iterable[Assertion]] = fromTryCatch {
    assertions.values filter { _.runId == runId }
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
    
}
