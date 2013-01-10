package org.w3.vs

import org.w3.vs.model._
import org.joda.time.{ Duration => _, _ }
import org.w3.util.{ URL, Util }
import org.w3.util.Util._
import scala.concurrent.util._
import java.io._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.store.MongoStore

object Main {

  def test(jobIdS: String): Unit = {
    implicit val conf = new DefaultProdConfiguration { }

    val jobId = JobId(jobIdS)
    val Some((run, urls, assertorCalls)) = Job.getLastRun(jobId).getOrFail()
//    println("3 " + run.toBeExplored)

    if (urls.size < 10)
      println("urls: " + urls)
    else
      println("urls: " + urls.size)
    println("assertorCalls: " + assertorCalls.size)

    conf.system.shutdown()
    conf.system.awaitTermination()
    conf.httpClient.close()
    conf.connection.close()

    println("you need to press ctrl-c")

  }



  def stressTestData(n: Int): Unit = {
    implicit val conf = new DefaultProdConfiguration { }

    def makeUser(name: String): User = User(userId = UserId(), email = name + "@w3.org", name = name, password = "secret", isSubscriber = true)

    val script = for {
      _ <- MongoStore.reInitializeDb()
      _ <- User.collection.create()
      _ <- Job.collection.create()
      _ <- Run.collection.create()
    } yield ()

    script.getOrFail()

    (1 to n) foreach { i =>
      val user = makeUser("user" + i)
      User.save(user).getOrFail()
    }

    conf.system.shutdown()
    conf.system.awaitTermination()
    conf.httpClient.close()
    conf.connection.close()

    println("you need to press ctrl-c")

  }

  def defaultData(): Unit = {
    
    implicit val conf = new DefaultProdConfiguration { }

    val tgambet = User(userId = UserId(), email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret", isSubscriber = false)

    val bertails = User(userId = UserId(), email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret", isSubscriber = true)

    val bernard = User(userId = UserId(), email = "bgidon@w3.org", name = "Bernard Gidon", password = "bernar", isSubscriber = true)

    val ralph = User(userId = UserId(), email = "swick@w3.org", name = "Ralph R. Swick", password = "secret", isSubscriber = true)

    val w3team = User(userId = UserId(), email = "w3t@w3.org", name = "W3C Team", password = "w3team", isSubscriber = true)
    
    val w3 = Job(
      id = JobId(),
      createdOn = DateTime.now(DateTimeZone.UTC),
      name = "W3C",
      creatorId = tgambet.id,
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/"),
        linkCheck = false,
        maxResources = 10,
        filter = Filter(include = Everything, exclude = Nothing),
        assertorsConfiguration = AssertorsConfiguration.default),
      status = NeverStarted,
      latestDone = None)
        
    val tr = Job(
      id = JobId(),
      createdOn = DateTime.now.plus(1000),
      name = "TR",
      creatorId = bertails.id,
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/TR"),
        linkCheck = false,
        maxResources = 10,
        filter=Filter.includePrefixes("http://www.w3.org/TR"),
        assertorsConfiguration = AssertorsConfiguration.default),
      status = NeverStarted,
      latestDone = None)

    val List(w3c1, w3c2, w3c3, w3c4, w3c5, w3c6) = List(1, 2, 3, 4, 5, 6) map { i =>
      Job(
        id = JobId(),
        createdOn = DateTime.now.plus(1000),
        name = s"""w3c${i}""",
        creatorId = bertails.id,
        strategy = Strategy(
          entrypoint = URL("http://www.w3.org"),
          linkCheck = false,
          maxResources = 100,
          filter=Filter.includePrefixes("http://www.w3.org/TR"),
          assertorsConfiguration = AssertorsConfiguration.default),
      status = NeverStarted,
      latestDone = None)
    }

    val ibm = Job(
      id = JobId(),
      createdOn = DateTime.now.plus(2000),
      name = "IBM",
      creatorId = bertails.id,
      strategy = Strategy(
        entrypoint = URL("http://www.ibm.com"),
        linkCheck = false,
        maxResources = 20,
        filter = Filter(include=Everything, exclude=Nothing),
        assertorsConfiguration = AssertorsConfiguration.default),
      status = NeverStarted,
      latestDone = None)
      
    val lemonde = Job(
      id = JobId(),
      createdOn = DateTime.now.plus(3000),
      name = "Le Monde",
      creatorId = tgambet.id,
      strategy = Strategy(
        entrypoint = URL("http://www.lemonde.fr"),
        linkCheck = false,
        maxResources = 30,
        filter = Filter(include = Everything, exclude = Nothing),
        assertorsConfiguration = AssertorsConfiguration.default),
      status = NeverStarted,
      latestDone = None)

    val script = for {
      _ <- MongoStore.reInitializeDb()
      _ <- User.save(tgambet)
      _ <- User.save(bertails)
      _ <- User.save(bernard)
      _ <- User.save(ralph)
      _ <- User.save(w3team)
      _ <- Job.save(w3)
      _ <- Job.save(w3c1)
      _ <- Job.save(w3c2)
      _ <- Job.save(w3c3)
      _ <- Job.save(w3c4)
      _ <- Job.save(w3c5)
      _ <- Job.save(w3c6)
      _ <- Job.save(tr)
      _ <- Job.save(ibm)
      _ <- Job.save(lemonde)
      _ <- User.save(User.sample) // The sample user for the demo
      _ <- Job.save(Job.sample)   // The sample job
    } yield ()

    script.getOrFail(10.seconds)
    
    conf.connection.close()
    conf.httpClient.close()
    conf.system.shutdown()
    conf.system.awaitTermination()

    println("you need to press ctrl-c")

  }
  
  def main(args: Array[String]): Unit = {
    
    val int = new Object {
      def unapply(s: String): Option[Int] = try Some(s.toInt) catch { case _: NumberFormatException => None }
    }

    args match {
      case Array("migration") => {
        val conf = new DefaultProdConfiguration { }
        //org.w3.vs.store.Formats26Dec.migration()(conf)
        println("done")
      }
      case Array("test", jobIdS) => test(jobIdS)
      case Array(int(n)) => stressTestData(n)
      case Array() => defaultData()
      case _ => sys.error("check your parameters")
    }

  }
  
  
}
