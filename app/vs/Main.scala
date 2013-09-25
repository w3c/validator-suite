package org.w3.vs

import org.w3.vs.model._
import org.joda.time.{ Duration => _, _ }
import org.w3.vs.util.{ In, Out, timer }
import org.w3.vs.web._
import org.w3.vs.util.timer._
import scala.concurrent.duration.Duration
import java.io._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.store.MongoStore
import scala.concurrent.Future
import play.api.Mode.Prod
import org.mozilla.javascript.tools.shell.Main.{main => executeJs}
import play.api.cache.EhCachePlugin

object Main {

  def cacheW3C(): Unit = {
    import org.w3.vs.web._
    val urls = List(
      "http://www.w3.org",
      "http://www.w3.org/",
      "http://www.w3.org/2008/site/css/advanced",
      "http://www.w3.org/2008/site/css/minimum",
      "http://www.w3.org/2008/site/css/minimum.css",
      "http://www.w3.org/2008/site/css/print",
      "http://www.w3.org/2008/site/css/print.css",
      "http://www.w3.org/2008/site/css/realprint.css",
      "http://www.w3.org/2008/site/images/favicon.ico",
      "http://www.w3.org/Consortium/membership",
      "http://www.w3.org/Help/",
      "http://www.w3.org/News/atom.xml",
      "http://www.w3.org/participate/",
      "http://www.w3.org/standards/"
    )
    val cacheDirectory = new File("http-cache")
    val cache = Cache(cacheDirectory)
    cache.reset()
    urls foreach { url => cache.retrieveAndCache(URL(url), GET) }
  }

  def test(jobIdS: String)(implicit conf: Database): Unit = {
    val jobId = JobId(jobIdS)
    val job = Job.get(jobId).getOrFail()
    job.latestDone match {
      case None => println("nothing to do")
      case Some(Done(runId, reason, completedOn, runData)) =>
        val (run, actions) = Run.get(runId).getOrFail(Duration("60s"))
        ()
//        if (urls.size < 10)
//          println("urls: " + urls)
//        else
//          println("urls: " + urls.size)
//        println("assertorCalls: " + assertorCalls.size)
    }

    println("you need to press ctrl-c")

  }

  def stressTestData(n: Int)(implicit conf: Database): Unit = {

    def makeUser(name: String): User =
      User.create(name = name, email = s"${name}@w3.org", password = "secret", credits = 1000000, optedIn = false, isSubscriber = false, isRoot = true)

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

    println("you need to press ctrl-c")

  }

  def currentUsers(): List[String] = {
    import play.api.Play.current
    import scala.collection.JavaConversions._
    current.plugin[EhCachePlugin].map{ p: EhCachePlugin =>
      val removedKeys = p.manager.asInstanceOf[net.sf.ehcache.CacheManager].getCache("play").getKeys.toList.map(_.toString)
      removedKeys
    }.getOrElse(List.empty)
  }

  def clearCache() {
    for {
      current <- play.api.Play.maybeApplication
      p <- current.plugin[EhCachePlugin]
    } {
      p.manager.clearAll
    }
  }

  def defaultData()(implicit conf: Database): Unit = {

    val tgambet = User.create(
      email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret",
      credits = 10000, optedIn = true, isSubscriber = false, isRoot = true)

    val bertails = User.create(
      email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret",
      credits = 10000, optedIn = true, isSubscriber = false, isRoot = true)

    val ted = User.create(
      email = "ted@w3.org", name = "Ted Guild", password = "secret",
      credits = 10000, optedIn = true, isSubscriber = false, isRoot = true)

    val bernard = User.create(
      email = "bgidon@w3.org", name = "Bernard Gidon", password = "bernar",
      credits = 10000, optedIn = true, isSubscriber = false, isRoot = true)

    val ralph = User.create(
      email = "swick@w3.org", name = "Ralph R. Swick", password = "secret",
      credits = 10000, optedIn = true, isSubscriber = false, isRoot = true)

    val vivien = User.create(
      email = "vivien@w3.org", name = "Vivien Lacourba", password = "secret",
      credits = 10000, optedIn = true, isSubscriber = false, isRoot = true)

    val w3team = User.create(
      email = "w3t@w3.org", name = "W3C Team", password = "w3team",
      credits = 10000, optedIn = true, isSubscriber = false, isRoot = true)
    
    val w3 = Job(
      name = "W3C",
      creatorId = Some(tgambet.id),
      strategy = Strategy(URL("http://www.w3.org/"), 10)
    )

    val w3Public = Job(
      name = "W3C",
      creatorId = None,
      strategy = Strategy(URL("http://www.w3.org/"), 100)
    )
        
    val tr = Job(
      createdOn = DateTime.now.plus(1000),
      name = "TR",
      creatorId =  Some(bertails.id),
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/TR"),
        maxResources = 10,
        filter = Filter.includePrefixes("http://www.w3.org/TR"))
      )

    val List(w3c1, w3c2, w3c3, w3c4, w3c5, w3c6) = List(1, 2, 3, 4, 5, 6) map { i =>
      Job(
        name = s"""w3c${i}""",
        creatorId = Some(bertails.id),
        strategy = Strategy(
          entrypoint = URL("http://www.w3.org/TR"),
          maxResources = 100))
    }

    val ibm = Job(
      name = "IBM",
      creatorId = Some(bertails.id),
      strategy = Strategy(
        entrypoint = URL("http://www.ibm.com"),
        maxResources = 20)
      )
      
    val lemonde = Job(
      name = "Le Monde",
      creatorId = Some(tgambet.id),
      strategy = Strategy(URL("http://www.lemonde.fr"), 30)
    )

    val script: Future[Unit] = for {
      _ <- MongoStore.reInitializeDb()
      _ <- Future.successful(clearCache())
      _ <- User.save(tgambet)
      _ <- User.save(bertails)
      _ <- User.save(ted)
      _ <- User.save(bernard)
      _ <- User.save(ralph)
      _ <- User.save(vivien)
      _ <- User.save(w3team)
      _ <- Job.save(w3)
      _ <- Job.save(w3Public)
      _ <- Job.save(w3c1)
      _ <- Job.save(w3c2)
      _ <- Job.save(w3c3)
      _ <- Job.save(w3c4)
      _ <- Job.save(w3c5)
      _ <- Job.save(w3c6)
      _ <- Job.save(tr)
      _ <- Job.save(ibm)
      _ <- Job.save(lemonde)
    } yield ()

    script.getOrFail(Duration("10s"))

  }

  def main(args: Array[String]): Unit = {
    val in = new In {
      def readLine(): String = scala.Console.readLine()
    }
    val out = new Out {
      def println(s: String) = System.out.println(s)
    }
    main(args, in, out)
  }
  
  def main(args: Array[String], in: In, out: Out): Unit = {
    import out.println

    val int = new Object {
      def unapply(s: String): Option[Int] = try Some(s.toInt) catch { case _: NumberFormatException => None }
    }

    args match {
      case Array("cache") => {
        cacheW3C()
      }
      case Array("migration") => {
        println("nothing planned right now")
      }
      case Array("test", jobIdS) => {
        val nconf = new ValidatorSuite { val mode = Prod }
        test(jobIdS)(nconf)
        nconf.shutdown()
      }
      case Array("run") =>  {
        val nconf = new ValidatorSuite { val mode = Prod }
        runJob()(nconf)
        nconf.shutdown()
      }
      case Array("createIndexes") =>  {
        val nconf = new ValidatorSuite { val mode = Prod }
        MongoStore.createIndexes()(nconf).getOrFail()
        nconf.shutdown()
        println("done")
      }
      case Array(int(n)) => {
        val nconf = new ValidatorSuite { val mode = Prod }
        stressTestData(n)(nconf)
        nconf.shutdown()
      }
      case Array("default") => {
        val nconf = new ValidatorSuite { val mode = Prod }
        defaultData()(nconf)
        nconf.shutdown()
        println("Database reset with default data")
      }
      case _ => {
        println("TODO")
      }
    }

  }
  
  def runJob()(implicit conf: ValidatorSuite): Unit = {
    val strategy =
      Strategy(
        entrypoint = URL("http://www.w3.org/"),
        linkCheck = false,
        maxResources = 20)

    val job = Job(name = "w3c", strategy = strategy, creatorId = None)

    org.w3.vs.assertor.LocalValidators.start()

    Job.save(job).getOrFail()

    job.run()

    job.resourceDatas() |>>> play.api.libs.iteratee.Iteratee.foreach(event => println("==========> " + event))

    job.jobDatas() |>>> play.api.libs.iteratee.Iteratee.foreach(jobData => println("&&&&&&&&> " + jobData))


  }
  
}
