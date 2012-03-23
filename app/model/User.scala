package org.w3.vs.model

import org.w3.vs.VSConfiguration
import org.w3.util._
import org.w3.util.Pimps._
import org.w3.vs.actor._
import scalaz._
import akka.dispatch._
import org.w3.vs.exception._

object User {

  def fake: User =
    User(organization = OrganizationData.fake.id, email = "foo@bar.com", name = "foo", password = "bar")

  def getJobs(organizationId: OrganizationId)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureValidation[SuiteException, Iterable[Job], Nothing, FALSE] = {
    import configuration.store
    for {
      jobConfs <- store.listJobs(organizationId).toDelayedValidation failMap (t => StoreException(t))
      jobs <- {
        val jobs = jobConfs map { jobConf => Jobs.getJobOrCreate(jobConf) }
        val futureJobs = Future.sequence(jobs)
        futureJobs.lift
      } failMap (t => StoreException(t))
    } yield {
      jobs
    }
  }
  
  def authenticate(email: String, password: String)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureValidation[SuiteException, Option[User], Nothing, FALSE] = {
    import configuration.store
    store.authenticate(email, password).toDelayedValidation failMap (t => StoreException(t))
  }
  
  def getByEmail(email: String)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureValidation[SuiteException, Option[User], Nothing, FALSE] = {
    import configuration.store
    store.getUserByEmail(email).toDelayedValidation failMap (t => StoreException(t))
  }

}

case class User(
    id: UserId = UserId.newId(),
    organization: OrganizationId,
    email: String,
    name: String,
    password: String)
