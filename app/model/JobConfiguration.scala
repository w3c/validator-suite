package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime
import org.w3.vs.actor._
import akka.dispatch._
import org.w3.vs.VSConfiguration

object JobConfiguration {

  def fake(strategy: Strategy): JobConfiguration = {
    val fakeUser = User.fake
    JobConfiguration(name = "fake job", creator = fakeUser.id, organization = fakeUser.organization, strategy = strategy)
  }

}


case class JobConfiguration(
    id: JobId = JobId.newId(),
    strategy: Strategy,
    createdOn: DateTime = new DateTime,
    creator: UserId,
    organization: OrganizationId,
    name: String) {

  def assignTo(user: User): JobConfiguration = {
    copy(creator = user.id, organization = user.organization)
  }

}
