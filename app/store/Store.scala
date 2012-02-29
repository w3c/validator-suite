package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.observer._
import org.w3.util._
import scalaz.Validation

trait Store {
  
  def putAssertion(assertion: Assertion): Validation[Throwable, Unit]
  
  def putResourceInfo(resourceInfo: ResourceInfo): Validation[Throwable, Unit]
  
  def putRun(run: Run): Validation[Throwable, Unit]
  
  def getResourceInfo(url: URL, runId: Run#Id): Validation[Throwable, ResourceInfo]
  
  def distance(url: URL, runId: Run#Id): Validation[Throwable, Int]
  
  def listResourceInfos(runId: Run#Id): Validation[Throwable, Iterable[ResourceInfo]]
  
  // this is not really safe (goes through the entire collection)
  // def listAllResourceInfos(): Validation[Throwable, Iterable[ResourceInfo]]
  
  def listAssertions(runId: Run#Id): Validation[Throwable, Iterable[Assertion]]
  
  def saveUser(user: User): Validation[Throwable, Unit]
  
  def getUserByEmail(email: String): Validation[Throwable, Option[User]]
  
  def authenticate(email: String, password: String): Validation[Throwable, Option[User]]
  
  
}

