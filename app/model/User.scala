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

case class UserId(private val uuid: UUID)

object UserId {

  def fromString(s: String): UserId = UserId(UUID.fromString(s))

  def newId(): UserId = UserId(UUID.randomUUID())

}

case class User(
    id: UserId = UserId.newId(),
    organization: Organization#Id,
    email: String,
    name: String,
    password: String) {

  type Id = UUID

}
