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

  def main(args: Array[String]): Unit = {
    migrationMarch28()
  }

  def migrationMarch28(): Unit = {

    val driver = new reactivemongo.api.MongoDriver
    val connection = driver.connection(Seq(node))

    val dbIn =
      connection(dbNameIn)

    val dbOut =
      connection(dbNameOut)

    dbOut.drop()

    val usersIn = dbIn("users")

    val usersOut: BSONCollection = dbOut("users")

    val jobsIn = dbIn("jobs")

    val jobsOut = dbOut("jobs")

    // copy users
    val users: List[JsValue] = usersIn.find(Json.obj()).cursor[JsValue].toList.getOrFail()

    users foreach { user =>
      println(user \ "email")
      usersOut.insert(user).getOrFail()
    }

    // copy jobs
    val jobs: List[JsValue] = jobsIn.find(Json.obj()).cursor[JsValue].toList.getOrFail()

    jobs foreach { json =>
      val job = Job(
        id = (json \ "_id").as[JobId],
        name = (json \ "name").as[String],
        createdOn = (json \ "createdOn").as[DateTime],
	strategy = (json \ "strategy").as[Strategy],
	creatorId = (json \ "creator").as[UserId],
	status = NeverStarted,
	latestDone = None)
//      println(json)
      println(job.name)
      jobsOut.insert(toJson(job)).getOrFail()
    }

    println("done with migrationMarch28")

  }

}
