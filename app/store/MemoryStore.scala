package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.observer._
import org.w3.util._
import scala.collection.JavaConverters._
import scala.collection.mutable.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap

class MemoryStore extends Store {
  
  val assertions: ConcurrentMap[Assertion#Id, Assertion] = new ConcurrentHashMap[Assertion#Id, Assertion]().asScala
    
  val resourceInfos: ConcurrentMap[ResourceInfo#Id, ResourceInfo] = new ConcurrentHashMap[ResourceInfo#Id, ResourceInfo]().asScala
  
  val runs: ConcurrentMap[Run#Id, Run] = new ConcurrentHashMap[Run#Id, Run]().asScala
  
  def init(): Either[Throwable, Unit] = Right()
  
  def putAssertion(assertion: Assertion): Either[Throwable, Unit] =
    try {
      assertions += assertion.id -> assertion
      Right()
    } catch {
      case t => Left(t)
    }
  
  def putResourceInfo(resourceInfo: ResourceInfo): Either[Throwable, Unit] =
    try {
      resourceInfos += resourceInfo.id -> resourceInfo
      Right()
    } catch {
      case t => Left(t)
    }
  
  def putRun(run: Run): Either[Throwable, Unit] =
    try {
      runs += run.id -> run
      Right()
    } catch {
      case t => Left(t)
    }
  
  def getResourceInfo(url: URL, runId: Run#Id): Either[Throwable, ResourceInfo] = {
    val riOpt = resourceInfos collectFirst { case (_, ri) if ri.url == url && ri.runId == runId => ri }
    riOpt.toRight(new Throwable("run %s: couldn't find %s" format (runId.toString, url.toString)))
  }
  
  def distance(url: URL, runId: Run#Id): Either[Throwable, Int] = {
    getResourceInfo(url, runId).right map { _.distancefromSeed }
  }
  
  def listResourceInfos(runId: Run#Id): Either[Throwable, Iterable[ResourceInfo]] = {
    val ris = resourceInfos.values filter { _.runId == runId }
    Right(ris)
  }
  
  def listAllResourceInfos(): Either[Throwable, Iterable[ResourceInfo]] =
    Right(resourceInfos.values)
  
  def listAssertions(runId: Run#Id): Either[Throwable, Iterable[Assertion]] = {
    val as = assertions.values filter { _.runId == runId }
    Right(as)
  }
  
}
