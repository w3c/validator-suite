package org.w3.vs

import org.w3.vs.model._
import org.joda.time._
import org.w3.util.URL
import akka.util.Duration
import java.io._

object Main {
  
  def delete(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles foreach delete
    if (!f.delete())
      throw new FileNotFoundException("Failed to delete file: " + f)
  }

  def main(args: Array[String]): Unit = {
    
    implicit val conf = new DefaultProdConfiguration { }

    if (conf.storeDirectory.exists)
      delete(conf.storeDirectory)

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
        filter = Filter(include = Everything, exclude = Nothing)),
      assertorConfiguration = AssertorConfiguration.default)
        
    val tr = Job(
      createdOn = DateTime.now.plus(1000),
      name = "TR",
      creator = bertails.id,
      organization = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/TR"),
        linkCheck = false,
        maxResources = 10,
        filter=Filter.includePrefixes("http://www.w3.org/TR")),
      assertorConfiguration = AssertorConfiguration.default)
          
    val ibm = Job(
      createdOn = DateTime.now.plus(2000),
      name = "IBM",
      creator = bertails.id,
      organization = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.ibm.com"),
        linkCheck = false,
        maxResources = 20,
        filter = Filter(include=Everything, exclude=Nothing)),
      assertorConfiguration = AssertorConfiguration.default)
      
    val lemonde = Job(
      createdOn = DateTime.now.plus(3000),
      name = "Le Monde",
      creator = bertails.id,
      organization = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.lemonde.fr"),
        linkCheck = false,
        maxResources = 30,
        filter = Filter(include = Everything, exclude = Nothing)),
      assertorConfiguration = AssertorConfiguration.default)

//    conf.blockingStore.readTransaction {
//      println("<<< "+conf.blockingStore.dg.size())
//    }

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

    script.result(Duration(3, "seconds")) fold (
      t => throw t,
      _ => println("loaded data successfully")
    )

//    conf.blockingStore.readTransaction {
//      println(">>> "+conf.blockingStore.dg.size())
//    }
    
    conf.store.shutdown()
    conf.system.shutdown()
    conf.system.awaitTermination()

    // must be missing one thread as we're not going back to the REPL...

  }
  
  
}
