package org.w3.vs.store

import org.w3.vs.model._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.{ Headers, URL }
import org.w3.util.html.Doctype
import org.w3.vs._
import org.w3.vs.actor.JobActor._

import scala.util._
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.api.collections.default._
import reactivemongo.bson._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.ReactiveBSONImplicits._
// Play Json imports
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Json.toJson
import play.api.libs.json.Reads.pattern
import org.w3.util.Util._

import org.w3.vs.store.Formats._

object Migration {

  val node = "localhost:27017"
  val dbNameIn = "vs"
  val dbNameOut = "vs-test"

  def migrationMarch28()(implicit conf: VSConfiguration): Unit = {
    import conf._

    val driver = new reactivemongo.api.MongoDriver
    val conn = driver.connection(Seq(node))

    val dbIn =
      connection(dbNameIn)(system.dispatchers.lookup("reactivemongo-dispatcher"))

    val dbOut =
      connection(dbNameOut)(system.dispatchers.lookup("reactivemongo-dispatcher"))

    val usersIn = dbIn("users", failoverStrategy = FailoverStrategy(retries = 0))

    val usersOut: BSONCollection = dbOut("users", failoverStrategy = FailoverStrategy(retries = 0))

    val jobsIn = dbIn("jobs", failoverStrategy = FailoverStrategy(retries = 0))

    val jobsOut = dbOut("jobs", failoverStrategy = FailoverStrategy(retries = 0))

    // copy users
    val users: List[JsValue] = usersIn.find(Json.obj()).cursor[JsValue].toList.getOrFail()

    users foreach { user =>
      usersOut.insert(user).getOrFail()
    }

    // copy jobs
    val jobs: List[JsValue] = jobsIn.find(Json.obj()).cursor[JsValue].toList.getOrFail()

    jobs foreach { json =>
      val job = json.as[Job].copy(status = NeverStarted, latestDone = None)
      jobsOut.insert(toJson(job)).getOrFail()
    }

    println("done with migrationMarch28")

  }

}
