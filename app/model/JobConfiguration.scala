package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime
import org.w3.vs.run._
import akka.dispatch._
import org.w3.vs.VSConfiguration

object JobConfiguration {

  def fake(strategy: EntryPointStrategy): JobConfiguration = {
    val fakeUser = User.fake
    JobConfiguration(name = "fake job", creator = fakeUser.id, organization = fakeUser.organization, strategy = strategy)
  }

}

case class JobConfigurationId(private val uuid: UUID) {

  def fromString(s: String): JobConfigurationId = JobConfigurationId(UUID.fromString(s))

  def newId(): JobConfigurationId = JobConfigurationId(UUID.randomUUID())

}

// I guess the case class is actually just the configuration part
// we should have a wrapper for that guy that would act as a facade for the JobActor
// this is now called Job, but that sounds wrong
case class JobConfiguration(
    // TODO id: JobConfigurationId = JobConfigurationId.newId(),
    id: JobConfiguration#Id = UUID.randomUUID,
    strategy: EntryPointStrategy,
    createdAt: DateTime = new DateTime,
    creator: User#Id = null,
    organization: Organization#Id = null,
    name: String) {

  type Id = UUID

  def shortId: String = id.toString.substring(0, 6)

  def assignTo(user: User): JobConfiguration = {
    copy(creator = user.id, organization = user.organization)
  }
}
