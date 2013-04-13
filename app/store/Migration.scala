package org.w3.vs.store

import org.w3.vs.model._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.vs.util.{ Headers, URL }
import org.w3.vs.util.html.Doctype
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
import play.modules.reactivemongo.json.ImplicitBSONHandlers._
// Play Json imports
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Json.toJson
import play.api.libs.json.Reads.pattern
import org.w3.vs.util.Util._

import org.w3.vs.store.Formats._

object Migration {

  val node = "localhost:27017"
  val dbName = "vs-test"

  def makeAllPasswordsLowerCase(): Unit = {
    val driver = new reactivemongo.api.MongoDriver
    val connection = driver.connection(Seq(node))
    val db = connection(dbName)

    val collection = db("users")

    val users: List[JsValue] = collection.find(Json.obj()).cursor[JsValue].toList.getOrFail()

    users foreach { user =>
      val email = (user \ "email").as[String]
      println(email)
      collection.update(
        selector = Json.obj("email" -> toJson(email)),
        update = Json.obj("$set" -> Json.obj("email" -> toJson(email.toLowerCase())))
      ).getOrFail()
    }

  }

  def main(args: Array[String]): Unit = {
    makeAllPasswordsLowerCase()
  }

}
