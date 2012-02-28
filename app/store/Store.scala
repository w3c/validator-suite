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
  
  // this is not really safe (goes through the entire collection)
  // def listAllResourceInfos(): Either[Throwable, Iterable[ResourceInfo]]
  
  def listAssertions(runId: Run#Id): Either[Throwable, Iterable[Assertion]]
  
  def saveUser(user: User): Either[Throwable, Unit]
  
  def getUserByEmail(email: String): Either[Throwable, Option[User]]
  
  def authenticate(email: String, password: String): Either[Throwable, Option[User]]
  
  
}

