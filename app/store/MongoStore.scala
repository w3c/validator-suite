package org.w3.vs.store

import org.w3.vs.model._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.vs.web.URL
import org.w3.vs._
import org.w3.vs.actor.JobActor._
import scala.util._

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
// Reactive Mongo plugin
import play.modules.reactivemongo._
// Play Json imports
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Json.toJson
import play.api.libs.json.Reads.pattern
import reactivemongo.api.indexes._
import reactivemongo.core.commands.GetLastError
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** utility functions to deal with a mongodb instance for the W3C Validator Suite
  */
object MongoStore {

  val journalCommit = GetLastError(j = true)

  def reInitializeDb()(implicit conf: Database): Future[Unit] = {
    for {
      _ <- conf.db.drop()
      _ <- initializeDb()
    } yield ()
  }

  def createCollections()(implicit conf: Database): Future[Unit] = {
    for {
      _ <- User.collection.create()
      _ <- Job.collection.create()
      _ <- Run.collection.create()
      _ <- Coupon.collection.create()
    } yield ()
  }

  def createIndexes()(implicit conf: Database): Future[Unit] = {
    import IndexType.Ascending
    val indexesManager = conf.db.indexesManager
    val runIndexesManager = indexesManager.onCollection(Run.collection.name)
    for {
      _ <- indexesManager.onCollection(User.collection.name).ensure(Index(
        name = Some("by-email"),
        key = List("email" -> Ascending), unique = true))
      _ <- indexesManager.onCollection(Job.collection.name).ensure(Index(
        name = Some("by-creator-id"),
        key = List("creator" -> Ascending)))
//      _ <- runIndexesManager.delete("_id_")
      _ <- runIndexesManager.ensure(Index(
        name = Some("by-jobId-then-event"),
        key = List("jobId" -> Ascending, "event" -> Ascending)))
      _ <- runIndexesManager.ensure(Index(
        name = Some("by-runId"),
        key = List("runId" -> Ascending)))
      _ <- indexesManager.onCollection(Coupon.collection.name).ensure(Index(
        name = Some("by-code"),
        key = List("code" -> Ascending), unique = true))
    } yield ()
  }


  def initializeDb()(implicit conf: Database): Future[Unit] = {
    for {
      _ <- createCollections()
      _ <- createIndexes()
    } yield ()
  }

}
