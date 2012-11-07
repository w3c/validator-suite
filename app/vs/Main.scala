package org.w3.vs

import org.w3.vs.model._
import org.joda.time.{ Duration => _, _ }
import org.w3.util.{ URL, Util }
import org.w3.util.Util._
import scala.concurrent.util._
import java.io._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.banana._

object Main {

  def stressTestData(n: Int): Unit = {
    implicit val conf = new DefaultProdConfiguration { }

    if (conf.storeDirectory.exists)
      Util.delete(conf.storeDirectory)

    def makeUser(name: String): (Organization, User) = {
      val orgId = OrganizationId()
      val user = User(userId = UserId(), organization = Some(orgId), email = name + "@w3.org", name = name, password = "secret")
      val org = Organization(orgId = orgId, name = name, admin = user.id)
      (org, user)
    }
    
    (1 to n) foreach { i =>
      val (org, user) = makeUser("user" + i)
      User.save(user).getOrFail()
      Organization.save(org).getOrFail()
    }

    conf.store.shutdown()
    conf.system.shutdown()
    conf.system.awaitTermination()

  }

  def defaultData(): Unit = {
    
    implicit val conf = new DefaultProdConfiguration { }

    if (conf.storeDirectory.exists)
      Util.delete(conf.storeDirectory)

    val orgId = OrganizationId()

    val tgambet = User(userId = UserId(), organization = Some(orgId), email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret")

    val bertails = User(userId = UserId(), organization = Some(orgId), email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret")

    val ralph = User(userId = UserId(), organization = Some(orgId), email = "swick@w3.org", name = "Ralph R. Swick", password = "secret")

    val w3team = User(userId = UserId(), organization = Some(orgId), email = "w3t@w3.org", name = "W3C Team", password = "w3team")

    val w3c = Organization(orgId = orgId, name = "W3C", admin = tgambet.id)
    
    val w3 = Job(
      createdOn = DateTime.now(DateTimeZone.UTC),
      name = "W3C",
      creator = bertails.id,
      organization = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/"),
        linkCheck = false,
        maxResources = 2,
        filter = Filter(include = Everything, exclude = Nothing),
        assertorsConfiguration = AssertorsConfiguration.default))
        
    val tr = Job(
      createdOn = DateTime.now.plus(1000),
      name = "TR",
      creator = bertails.id,
      organization = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/TR"),
        linkCheck = false,
        maxResources = 10,
        filter=Filter.includePrefixes("http://www.w3.org/TR"),
        assertorsConfiguration = AssertorsConfiguration.default))
          
    val ibm = Job(
      createdOn = DateTime.now.plus(2000),
      name = "IBM",
      creator = bertails.id,
      organization = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.ibm.com"),
        linkCheck = false,
        maxResources = 20,
        filter = Filter(include=Everything, exclude=Nothing),
        assertorsConfiguration = AssertorsConfiguration.default))
      
    val lemonde = Job(
      createdOn = DateTime.now.plus(3000),
      name = "Le Monde",
      creator = bertails.id,
      organization = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.lemonde.fr"),
        linkCheck = false,
        maxResources = 30,
        filter = Filter(include = Everything, exclude = Nothing),
        assertorsConfiguration = AssertorsConfiguration.default))

    val script = for {
      _ <- User.save(tgambet)
      _ <- User.save(bertails)
      _ <- User.save(ralph)
      _ <- User.save(w3team)
      _ <- Organization.save(w3c)
      _ <- Job.save(w3)
      _ <- Job.save(tr)
      _ <- Job.save(ibm)
      _ <- Job.save(lemonde)
    } yield ()

    script.getOrFail(10.seconds)
    
    conf.store.shutdown()
    conf.system.shutdown()
    conf.system.awaitTermination()

  }
  
  def main(args: Array[String]): Unit = {
    
    val int = new Object {
      def unapply(s: String): Option[Int] = try Some(s.toInt) catch { case _: NumberFormatException => None }
    }

    args match {
      case Array(int(n)) => stressTestData(n)
      case _ => defaultData()
    }

  }
  
  
}
