package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.vs.actor._
import scalaz._
import Scalaz._
import akka.dispatch._
import org.w3.vs.exception._

object User {
  
  def get(id: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, User] = sys.error("")
  def getForOrganization(id: OrganizationId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[User]] = sys.error("") 
  
  // TODO: For now these only fail with StoreExceptions but should also fail with a Unauthorized exception 
  def authenticate(email: String, password: String)(implicit conf: VSConfiguration): FutureVal[Exception, User] = {
    val tgambet = User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret", organizationId = OrganizationId())
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(tgambet)
  }
  
  def getByEmail(email: String)(implicit conf: VSConfiguration): FutureVal[Exception, User] = {
    val tgambet = User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret", organizationId = OrganizationId())
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(tgambet)
  }
  
  def save(user: User)(implicit conf: VSConfiguration): FutureVal[Exception, User] = sys.error("")

}



case class User (
    id: UserId = UserId(),
    name: String,
    email: String,
    password: String,
    organizationId: OrganizationId)(implicit conf: VSConfiguration) {
  
  def getOrganization: FutureVal[Exception, Organization] = Organization.get(organizationId)
  
  def getJobs: FutureVal[Exception, Iterable[Job]] = Job.getFor(this)
  
  // getJob with id only if owned by user. should probably be a db request directly.
  def getJob(id: JobId): FutureVal[Exception, Job] = {
    getJobs map {
      jobs => jobs.filter(_.id === id).headOption
    } discard {
      case None => UnknownJob
    } map { _.get }
  }
  
  def save(): FutureVal[Exception, User] = User.save(this)
  def toValueObject: UserVO = UserVO(id, name, email, password, organizationId)
}
