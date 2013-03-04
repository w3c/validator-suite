package org.w3.vs.store

import org.w3.vs.model._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.{ Headers, URL }
import org.w3.vs._
import org.w3.vs.actor.JobActor._
import scala.util._

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.api.collections.default._
import reactivemongo.bson._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.ReactiveBSONImplicits._
// Play Json imports
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Json.toJson
import play.api.libs.json.Reads.pattern
import reactivemongo.api.indexes._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** utility functions to deal with a mongodb instance for the Validator Suite
  */
object MongoStore {

  def reInitializeDb()(implicit conf: VSConfiguration): Future[Unit] = {
    for {
      _ <- conf.db.drop()
      _ <- initializeDb()
    } yield ()
  }

  def initializeDb()(implicit conf: VSConfiguration): Future[Unit] = {
    import IndexType.Ascending
    for {
      _ <- User.collection.create()
      _ <- Job.collection.create()
      _ <- Run.collection.create()
      indexesManager = conf.db.indexesManager
      _ <- indexesManager.onCollection(User.collection.name).ensure(Index(List("email" -> Ascending), unique = true))
      _ <- indexesManager.onCollection(Job.collection.name).ensure(Index(List("creator" -> Ascending)))
      runIndexesManager = indexesManager.onCollection(Run.collection.name)
      _ <- runIndexesManager.ensure(Index(List("jobId" -> Ascending, "event" -> Ascending)))
      _ <- runIndexesManager.ensure(Index(List("runId" -> Ascending)))
    } yield ()
  }

}
