package org.w3.vs.model

import org.w3.vs.VSConfiguration
import org.w3.util._
import org.w3.util.Pimps._
import org.w3.vs.actor._
import scalaz._
import akka.dispatch._

object User {

  def fake: User =
    User(organization = OrganizationData.fake.id, email = "foo@bar.com", name = "foo", password = "bar")

  def getJobs(organizationId: OrganizationId)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureValidation[Throwable, Iterable[Job]] = {
    import configuration.store
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

}

case class User(
    id: UserId = UserId.newId(),
    organization: OrganizationId,
    email: String,
    name: String,
    password: String)
