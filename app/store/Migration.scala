package org.w3.vs.store

import org.w3.vs.model._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.{ Headers, URL }
import org.w3.util.html.Doctype
import org.w3.vs._
import org.w3.vs.actor.JobActor._
import org.w3.vs.actor.AssertorCall

import scala.util._
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._
// Play Json imports
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Json.toJson
import play.api.libs.json.Reads.pattern

import org.w3.vs.store.Formats._

object MigrationFeb15 {

  import org.mindrot.jbcrypt.BCrypt

  def updatePasswords()(implicit conf: VSConfiguration): Unit = {
    import conf._
    val query = Json.obj()
    val cursor = User.collection.find[JsValue, JsValue](query)
    val future = cursor.enumerate() &> Enumeratee.map[JsValue] { json =>
      val userId = (json \ "_id").as[UserId]
      val password = (json \ "password").as[String]
      val hash = BCrypt.hashpw(password, BCrypt.gensalt())
      val selector = Json.obj("_id" -> toJson(userId))
      val update = Json.obj("$set" -> Json.obj("password" -> toJson(hash)))
      val f: Future[Unit] = User.collection.update[JsValue, JsValue](selector, update) map { lastError => () }
      f.onComplete {
        case Success(_) => println(s"hashed password for user ${userId}")
        case Failure(_) => println(s"something wrong happened with user ${userId}")
      }
      f
    } |>>> Iteratee.foldM[Future[Unit], Unit](()){ case (_, x) => x }
    Await.result(future, Duration("60s"))
  }

}
