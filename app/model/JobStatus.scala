package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.akkaext._
import akka.actor._
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import scalaz.Equal
import scalaz.Equal._
import org.w3.vs._
import org.w3.vs.actor.JobActor._
import scala.concurrent.{ ops => _, _ }
import scala.concurrent.ExecutionContext.Implicits.global

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._
import org.w3.vs.actor.AssertorCall

sealed trait JobStatus

case object NeverStarted extends JobStatus
case class Running(runId: RunId, actorPath: ActorPath) extends JobStatus
case class Done(
  runId: RunId,
  reason: DoneReason,
  // TODO: this is repeated in jobData <- to be fixed
  completedOn: DateTime,
  jobData: JobData) extends JobStatus

sealed trait DoneReason
case object Stopped extends DoneReason
case object Completed extends DoneReason
