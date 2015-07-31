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
import collection.immutable.Iterable
import org.joda.time.DateTime

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

  /*def test(jobIdS: String)(implicit conf: Database): Unit = {
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

  }*/

  def stressTestData(n: Int)(implicit conf: ValidatorSuite with Database): Unit = {

    def makeUser(name: String): User =
      User.create(name = name, email = s"${name}@exapmle.com", password = "secret", credits = 1000000, optedIn = false, isSubscriber = false, isRoot = true)

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

/*  def currentUsers(): List[String] = {
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
  }*/

  def setRootUser(email: String)(implicit conf: ValidatorSuite with Database): Future[String] = {
    User.getByEmail(email).flatMap( user =>
      user.isRoot match {
        case true => Future.successful(s"${email} is already a root user.")
        case false => User.update(user.copy(isRoot = true)).map(_ => s"${email} set as a root user.")
      }
    )
  }

  def setRootUser(id: UserId)(implicit conf: ValidatorSuite with Database): Future[String] = {
    User.get(id).flatMap( user =>
      user.isRoot match {
        case true => Future.successful(s"${user.email} is already a root user.")
        case false => User.update(user.copy(isRoot = true)).map(_ => s"${user.email} (${id}}) set as a root user.")
      }
    )
  }

  def addRootUsers()(implicit conf: ValidatorSuite with Database): Future[Iterable[String]] = {

    val roots = Map(
      "Admin User" -> "admin@example.com"
    ) map { case (name, email) =>
      User.create(
        email = email, name = name, password = "password",
        credits = 10000, optedIn = false, isSubscriber = false, isRoot = true)
    }

    Future.sequence(
      roots.map( root =>
        setRootUser(root.email) recoverWith { case _ =>
          User.save(root) map { _ =>
            s"${root.email} added to database as root."
          } recover { case e: Exception =>
            s"${root.email} could not be added to the db: ${e.getMessage}"
          }
        }
      )
    )

  }

  def defaultData()(implicit conf: ValidatorSuite with Database): Unit = {

    val w3team = User.create(
      email = "test@example.com", name = "Test User", password = "password",
      credits = 1000, optedIn = false, isSubscriber = false, isRoot = false)
    
    val script: Future[Unit] = for {
      _ <- MongoStore.reInitializeDb()
      _ <- addRootUsers()
      _ <- User.save(w3team)
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
      /*case Array("test", jobIdS) => {
        val nconf = new ValidatorSuite { val mode = Prod }
        test(jobIdS)(nconf)
        nconf.shutdown()
      }*/
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
        maxResources = 20)

    val job = Job(name = "w3c", strategy = strategy, creatorId = None)

    //org.w3.vs.assertor.LocalValidators.start()

    Job.save(job).getOrFail()

    job.run()

    job.resourceDatas(forever = false) |>>> play.api.libs.iteratee.Iteratee.foreach(event => println("==========> " + event))

    job.jobDatas() |>>> play.api.libs.iteratee.Iteratee.foreach(jobData => println("&&&&&&&&> " + jobData))


  }
  
}
