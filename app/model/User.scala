package org.w3.vs.model

import org.w3.util._
import scalaz._
import Scalaz._
import org.w3.vs.exception._
import org.w3.banana._
import org.w3.banana.util._
import org.w3.banana.LinkedDataStore._
import org.w3.vs._
import diesel._
import org.w3.vs.store.Binders._
import org.w3.vs.sparql._
import org.w3.banana.util._

case class User(id: UserId, vo: UserVO)(implicit conf: VSConfiguration) {
  
  import conf._

  val userUri = id.toUri

  val ldr: LinkedDataResource[Rdf] = LinkedDataResource[Rdf](userUri, vo.toPG)

  val orgUriOpt = vo.organization map (_.toUri)

  def getOrganization(): FutureVal[Exception, Option[Organization]] = {
    val r: BananaFuture[Option[Organization]] = orgUriOpt match {
      case Some(orgUri) => Organization.bananaGet(orgUri) map (Some(_))
      case None => none[Organization].bf
    }
    r.toFutureVal
  }

  // getJob with id only if owned by user. should probably be a db request directly.
  def getJob(jobId: JobId): FutureVal[Exception, Job] = {
    Job.getFor(id) map {
      jobs => jobs.filter(_.id === jobId).headOption
    } discard {
      case None => UnknownJob
    } map { _.get }
  }
  
  def save(): FutureVal[Exception, Unit] = User.save(this)
  
  def delete(): FutureVal[Exception, Unit] = User.delete(this)

}

object User {
  
  val logger = play.Logger.of(classOf[User])

  def apply(
    userId: UserId,
    name: String,
    email: String,
    password: String,
    organization: Option[OrganizationId])(
    implicit conf: VSConfiguration): User =
      User(userId, UserVO(name, email, password, organization))

  def bananaGet(userUri: Rdf#URI)(implicit conf: VSConfiguration): BananaFuture[User] = {
    import conf._
    for {
      userId <- userUri.as[UserId].bf
      userLDR <- store.GET(userUri)
      userVO <- userLDR.resource.as[UserVO]
    } yield new User(userId, userVO) { override val ldr = userLDR }
  }

  def get(userUri: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, User] = {
    import conf._
    bananaGet(userUri).toFutureVal
  }
  
  def get(id: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, User] =
    get(UserUri(id))

  def authenticate(email: String, password: String)(implicit conf: VSConfiguration): FutureVal[Exception, User] = {
    getByEmail(email) discard { 
      case user if (user.vo.password /== password) => Unauthenticated
    }
  }
  
  def getByEmail(email: String)(implicit conf: VSConfiguration): FutureVal[Exception, User] = {
    import conf._
    val query = """
CONSTRUCT {
  <local:user> <local:hasUri> ?user .
  ?s ?p ?o
} WHERE {
  graph ?userG {
    ?user ont:email ?email .
    ?s ?p ?o
  }
}
"""
    val construct = ConstructQuery(query, ont)
    val r = for {
      graph <- store.executeConstruct(construct, Map("email" -> email.toNode))
      as <- (PointedGraph[Rdf](URI("local:user"), graph) / URI("local:hasUri")).as2[UserId, UserVO].bf
    } yield User(as._1, as._2)
    r.toFutureVal failMap { _ => UnknownUser }
  }

  def save(vo: UserVO)(implicit conf: VSConfiguration): FutureVal[Exception, Rdf#URI] = {
    import conf._
    store.POSTToCollection(userContainer, vo.toPG).toFutureVal
  }
  
  def save(user: User)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf._
    store.PUT(user.ldr).toFutureVal
  }

  def delete(user: User)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    sys.error("")
    
}
