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

  // TODO: For now these only fail with StoreExceptions but should also fail with a Unauthorized exception 
  def authenticate(email: String, password: String)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureValidation[SuiteException, User, Nothing, NOTSET] = {
    import configuration.store
    store.authenticate(email, password)
  }
  
  def getByEmail(email: String)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureValidation[SuiteException, User, Nothing, NOTSET] = {
    import configuration.store
    store.getUserByEmail(email)
  }

}

case class User(
    id: UserId = UserId.newId(),
    organization: OrganizationId,
    email: String,
    name: String,
    password: String)
