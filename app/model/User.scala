package org.w3.vs.model

import java.util.UUID
import org.w3.vs.VSConfiguration
import org.w3.util._
import org.w3.util.Pimps._
import org.w3.vs.run._
import scalaz._
import akka.dispatch._

object User {

  def fake: User =
    User(organization = Organization.fake.id, email = "foo@bar.com", name = "foo", password = "bar")

  def getJobs(organizationId: Organization#Id)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureValidation[Throwable, Iterable[Job]] = {
    import configuration.store
    Future(store.listJobs(organizationId)).toFutureValidation map { _ map Job.getJobOrCreate }
  }

}

case class UserId(private val uuid: UUID) {

  def fromString(s: String): UserId = UserId(UUID.fromString(s))

  def newId(): UserId = UserId(UUID.randomUUID())

}

case class User(
    // TODO: id: UserId = UserId.newId(),
    id: User#Id = UUID.randomUUID,
    organization: Organization#Id,
    email: String,
    name: String,
    password: String) {

  type Id = UUID

  // TODO we shoudnl't swallow the Failure here, that's bad
  //  def owns(jobId: Job#Id)(implicit configuration: VSConfiguration): Boolean = {
  //    import configuration.store
  //    store.getJobById(jobId) match {
  //      case Success(Some(_)) => true
  //      case _ => false
  //    }
  //  }

}
