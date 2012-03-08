package org.w3.vs.model

import java.util.UUID
import org.w3.vs.VSConfiguration
import scalaz._

object User {
  def fake: User = User(organization = Organization.fake.id, email = "foo@bar.com", name = "foo", password = "bar")
}

case class User(
    id: User#Id = UUID.randomUUID,
    organization: Organization#Id,
    email: String,
    name: String,
    password: String) {
  
  type Id = UUID
  
  // TODO we shoudnl't swallow the Failure here, that's bad
  def owns(jobId: Job#Id)(implicit configuration: VSConfiguration): Boolean = {
    import configuration.store
    store.getJobById(jobId) match {
      case Success(Some(_)) => true
      case _ => false
    }
  }
  
}
