package org.w3.vs.model

//import play.api.db._
//import play.api.Play.current

//import anorm._
//import anorm.SqlParser._

import org.w3.vs.observer.{Observer => Job}
import org.w3.vs.observer._
import org.w3.vs.model._

case class User(
    email: String,
    name: String,
    password: String,
    jobs: List[Job]) {
  
  def withJob(job: Job): User =
    this.copy(jobs = jobs :+ job)
  
  def owns(job: Job): Boolean =
    jobs.contains(job)
  
  def canAccess(job: Job): Boolean = 
    true
}

//case class Job(strategy: Strategy, jobConf: JobConfiguration, observers: List[Observer])
//case class JobConfiguration()

object User {
  
  /**
   * Parse a User from a ResultSet
   */
  /*val simple = {
    get[String]("user.email") ~/
    get[String]("user.name") ~/
    get[String]("user.password") ^^ {
      case email~name~password => User(email, name, password)
    }}*/
  
  var users: Seq[User] = Seq[User]()
  
  def apply(email: String, name: String, password: String): User =
    User(email, name, password, List[Job]())
  
  // -- Queries
  
  /**
   * Retrieve a User from email.
   */
  def findByEmail(email: String): Option[User] = {
    users find { _.email == email}
  }
  
  /**
   * Retrieve all users.
   */
  def findAll: Seq[User] = {
    users
  }
  
  /**
   * Authenticate a User.
   */
  def authenticate(email: String, password: String): Option[User] = {
    users find { u => u.email == email && u.password == password }
  }
   
  /**
   * Create a User.
   */
  def create(user: User): User = {
    users +:= user
    user
  }
  
}