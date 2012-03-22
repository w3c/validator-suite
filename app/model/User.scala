package org.w3.vs.model

import org.w3.vs.VSConfiguration
import org.w3.util._
import org.w3.util.Pimps._
import org.w3.vs.actor._
import scalaz._
import akka.dispatch._
import org.w3.vs.exception._

object User {

  def store = org.w3.vs.Prod.configuration.store
  
  def fake: User =
    User(organization = OrganizationData.fake.id, email = "foo@bar.com", name = "foo", password = "bar")

  def getJobs(organizationId: OrganizationId)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureValidation[Throwable, Iterable[Job]] = {
    for {
      jobConfs <- store.listJobs(organizationId).toDelayedValidation
      jobs <- {
        val jobs = jobConfs map { jobConf => Jobs.getJobOrCreate(jobConf) }
        val futureJobs = Future.sequence(jobs)
        futureJobs.lift
      }
    } yield {
      jobs
    }
  }
  
  def authenticate(email: String, password: String): Validation[SuiteException, Option[User]] =
    store authenticate (email, password) failMap (t => StoreException(t))
  
  def getByEmail(email: String): Validation[SuiteException, Option[User]] =
    store getUserByEmail (email) failMap (t => StoreException(t))

}

case class User(
    id: UserId = UserId.newId(),
    organization: OrganizationId,
    email: String,
    name: String,
    password: String)
