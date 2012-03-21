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


case class JobConfiguration(
    id: JobId = JobId.newId(),
    strategy: EntryPointStrategy,
    createdAt: DateTime = new DateTime,
    creator: UserId,
    organization: OrganizationId,
    name: String) {

  def shortId: String = id.toString.substring(0, 6)

  def assignTo(user: User): JobConfiguration = {
    copy(creator = user.id, organization = user.organization)
  }
}
