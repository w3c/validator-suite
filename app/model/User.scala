package org.w3.vs.model

//import play.api.db._
//import play.api.Play.current

//import anorm._
//import anorm.SqlParser._

case class User(email: String, name: String, password: String)

object User {
  
  // -- Parsers
  
  /**
   * Parse a User from a ResultSet
   */
  /*val simple = {
    get[String]("user.email") ~/
    get[String]("user.name") ~/
    get[String]("user.password") ^^ {
      case email~name~password => User(email, name, password)
    }
  }*/
  
  // -- Queries
  
  /**
   * Retrieve a User from email.
   */
  def findByEmail(email: String): Option[User] = {
    /*DB.withConnection { implicit connection =>
      SQL("select * from user where email = {email}").on(
        'email -> email
      ).as(User.simple ?)
    }*/
    users find { _.email == email}
  }
  
  var users: Seq[User] = Seq[User]()
  
  /**
   * Retrieve all users.
   */
  def findAll: Seq[User] = {
    /*DB.withConnection { implicit connection =>
      SQL("select * from user").as(User.simple *)
    }*/
    users
  }
  
  /**
   * Authenticate a User.
   */
  def authenticate(email: String, password: String): Option[User] = {
    /*DB.withConnection { implicit connection =>
      SQL(
        """
         select * from user where 
         email = {email} and password = {password}
        """
      ).on(
        'email -> email,
        'password -> password
      ).as(User.simple ?)
    }*/
    users find { u => u.email == email && u.password == password }
    
  }
   
  /**
   * Create a User.
   */
  def create(user: User): User = {
    /*DB.withConnection { implicit connection =>
      SQL(
        """
          insert into user values (
            {email}, {name}, {password}
          )
        """
      ).on(
        'email -> user.email,
        'name -> user.name,
        'password -> user.password
      ).executeUpdate()
      
      user
      
    }*/
    users +:= user
    user
  }
  
}