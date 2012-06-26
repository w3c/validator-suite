package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import scalaz._
import Scalaz._
import org.w3.vs.exception._
import org.w3.banana._

object User {
  
  val logger = play.Logger.of(classOf[User])

  def apply(vo: UserVO)(implicit conf: VSConfiguration): User =
    User(vo.id, vo.name, vo.email, vo.password, vo.organizationId)

  def getUserVO(id: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, UserVO] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = UserUri(id)
    FutureVal(conf.store.getNamedGraph(uri)) flatMap { graph => 
      FutureVal.pureVal[Throwable, UserVO]{
        val pointed = PointedGraph(uri, graph)
        UserVOBinder.fromPointedGraph(pointed)
      }(t => t)
    }
  }
  
  def get(id: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, User] =
    getUserVO(id) map { User(_) }

  def authenticate(email: String, password: String)(implicit conf: VSConfiguration): FutureVal[Exception, User] = {
    getByEmail(email) discard { 
      case user if (user.password /== password) => Unauthenticated
    }
  }
  
  def getByEmail(email: String)(implicit conf: VSConfiguration): FutureVal[Exception, User] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders._
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?s ?p ?o
} WHERE {
  graph ?g {
    ?s ont:email "#email"^^xsd:string .
    ?s ?p ?o
  }
}
""".replaceAll("#email", email)
    val construct = SparqlOps.ConstructQuery(query, xsd, ont)
    FutureVal(store.executeConstruct(construct)) flatMap { graph =>
      FutureVal.pureVal[Throwable, UserVO]{
        graph.getAllInstancesOf(ont.User).as[UserVO]
      }(t => t) failMap { _ => UnknownUser }
    } map { User(_) }
  }

  def saveUserVO(vo: UserVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = UserVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(UserUri(vo.id), graph)
    FutureVal(result)
  }
  
  def save(user: User)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    saveUserVO(user.toValueObject)

  def delete(user: User)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    sys.error("")
    
}


case class User (
    id: UserId = UserId(),
    name: String,
    email: String,
    password: String,
    organizationId: OrganizationId)(implicit conf: VSConfiguration) {
  
  def getOrganization: FutureVal[Exception, Organization] = Organization.get(organizationId)
  
  def getJobs: FutureVal[Exception, Iterable[Job]] = Job.getFor(this)
  
  // getJob with id only if owned by user. should probably be a db request directly.
  def getJob(id: JobId): FutureVal[Exception, Job] = {
    getJobs map {
      jobs => jobs.filter(_.id === id).headOption
    } discard {
      case None => UnknownJob
    } map { _.get }
  }
  
  def save(): FutureVal[Exception, Unit] = User.save(this)
  
  def delete(): FutureVal[Exception, Unit] = User.delete(this)

  def toValueObject: UserVO = UserVO(id, name, email, password, organizationId)
}
