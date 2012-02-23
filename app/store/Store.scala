package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.observer._
import org.w3.util._

trait Store {
  
  def putAssertion(assertion: Assertion): Either[Throwable, Unit]
  
  def putResourceInfo(resourceInfo: ResourceInfo): Either[Throwable, Unit]
  
  def putRun(run: Run): Either[Throwable, Unit]
  
  def getResourceInfo(url: URL, runId: Run#Id): Either[Throwable, ResourceInfo]
  
  def distance(url: URL, runId: Run#Id): Either[Throwable, Int]
  
  def listResourceInfos(runId: Run#Id): Either[Throwable, Iterable[ResourceInfo]]
  
  def listAllResourceInfos(): Either[Throwable, Iterable[ResourceInfo]]
  
  def listAssertions(runId: Run#Id): Either[Throwable, Iterable[Assertion]]
  
//  def init(): Either[Throwable, Unit]
//  def list: Either[Throwable, Traversable[ObserverState]]
//  def get(id: ObserverId): Either[Throwable, ObserverState]
//  def save(state: ObserverState): Either[Throwable, Unit]
  
}

