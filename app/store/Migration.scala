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
    Await.result(cursor.toList, Duration("5s")) foreach { json =>
      val email = (json \ "email").as[String]
      val password = (json \ "password").as[String]
      val hash = BCrypt.hashpw(password, BCrypt.gensalt())
      val selector = Json.obj("email" -> toJson(email))
      val update = Json.obj("$set" -> Json.obj("password" -> toJson(hash)))
      println(s"selector: ${selector} - udpate: ${update}")
      val f: Future[Unit] = User.collection.update[JsValue, JsValue](selector, update) map { lastError => () }
      f.onComplete {
        case Success(_) => println(s"${email} got hashed ${hash}")
        case Failure(_) => println(s"something wrong happened with user ${email}")
      }
      Await.result(f, Duration("5s"))
    }
  }

}
