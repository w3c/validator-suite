package org.w3.vs.store

import org.w3.vs.model._
import org.w3.banana._
import org.w3.vs._
import diesel._
import diesel.ops._
import scala.util._

trait UriBuilders {

  def jobContainer(userId: UserId): Rdf#URI = UserUri(userId).fragmentLess / "jobs"

  implicit object JobUri extends URIBinder[Rdf, (UserId, JobId)] {

    val r = new scala.util.matching.Regex("""https://validator.w3.org/suite/users/([^/]+?)/jobs/([^/]+?)#thing""", "userId", "jobId")

    def apply(userId: UserId, jobId: JobId): Rdf#URI =
      jobContainer(userId).fragmentLess / jobId.id fragment "thing"

    def fromUri(uri: Rdf#URI): Try[(UserId, JobId)] =
      r findFirstIn uri.getString match {
        case Some(r(userId, jobId)) => Success(UserId(userId), JobId(jobId))
        case None => Failure(FailedConversion("JobUri.fromUri: " + uri.getString))
      }

    def toUri(t: (UserId, JobId)): Rdf#URI = apply(t._1, t._2)
  }

  def runContainer(userId: UserId, jobId: JobId): Rdf#URI = JobUri(userId, jobId).fragmentLess / "runs"

  implicit object RunUri extends URIBinder[Rdf, (UserId, JobId, RunId)] {

    val r = new scala.util.matching.Regex("""https://validator.w3.org/suite/users/([^/]+?)/jobs/([^/]+?)/runs/([^/]+?)#thing""", "userId", "jobId", "runId")

    def apply(userId: UserId, jobId: JobId, runId: RunId): Rdf#URI =
      runContainer(userId, jobId).fragmentLess / runId.id fragment "thing"

    def fromUri(uri: Rdf#URI): Try[(UserId, JobId, RunId)] =
      r findFirstIn uri.getString match {
        case Some(r(userId, jobId, runId)) => Success(UserId(userId), JobId(jobId), RunId(runId))
        case None => Failure(FailedConversion("RunUri.fromUri: " + uri.getString))
      }

    def toUri(t: (UserId, JobId, RunId)): Rdf#URI = apply(t._1, t._2, t._3)
  }

  val userContainer: Rdf#URI = URI("https://validator.w3.org/suite/users/")

  implicit object UserUri extends URIBinder[Rdf, UserId] {
    def apply(userId: UserId): Rdf#URI = userContainer / userId.id fragment "me"
    def fromUri(uri: Rdf#URI): Try[UserId] = {
      val str = uri.getString
      if (str.startsWith(userContainer.getString) && str.endsWith("#me"))
        Success(UserId(str.substring(str.lastIndexOf('/') + 1, str.lastIndexOf('#'))))
      else
        Failure(WrongExpectation("cannot extract UserId from " + str))
    }

    def toUri(t: UserId): Rdf#URI = apply(t)
  }

}
