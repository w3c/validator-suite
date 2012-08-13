package org.w3.vs.store

import org.w3.vs.model._
import org.w3.banana._
import scalaz._
import scalaz.Scalaz._
import org.w3.vs._
import diesel._
import ops._

trait UriBuilders {

//  implicit object AssertionUri
//  extends PrefixBuilder("", "https://validator.w3.org/suite/assertion/")(ops)
//  with URIBinder[Rdf, AssertionId] {
//    def apply(id: AssertionId): Rdf#URI = apply(id.toString)
//    def fromUri(uri: Rdf#URI): Validation[BananaException, AssertionId] =
//      getLocalName(uri) map AssertionId.apply
//    def toUri(t: AssertionId): Rdf#URI = apply(t)
//  }
//
//  implicit object ContextUri
//  extends PrefixBuilder("", "https://validator.w3.org/suite/context/")(ops)
//  with URIBinder[Rdf, ContextId] {
//    def apply(id: ContextId): Rdf#URI = apply(id.toString)
//    def fromUri(uri: Rdf#URI): Validation[BananaException, ContextId] = getLocalName(uri) map ContextId.apply
//    def toUri(t: ContextId): Rdf#URI = apply(t)
//  }
//
//  implicit object AssertorResultUri
//  extends PrefixBuilder("", "https://validator.w3.org/suite/assertorResult/")(ops)
//  with URIBinder[Rdf, AssertorResponseId] {
//    def apply(id: AssertorResponseId): Rdf#URI = apply(id.toString)
//    def fromUri(uri: Rdf#URI): Validation[BananaException, AssertorResponseId] = getLocalName(uri) map AssertorResponseId.apply
//    def toUri(t: AssertorResponseId): Rdf#URI = apply(t)
//  }



  def jobContainer(orgId: OrganizationId): Rdf#URI = OrganizationUri(orgId).fragmentLess / "jobs"

  implicit object JobUri extends URIBinder[Rdf, (OrganizationId, JobId)] {

    val r = new scala.util.matching.Regex("""https://validator.w3.org/suite/organizations/([^/]+?)/jobs/([^/]+?)#thing""", "organizationId", "jobId")

    def apply(orgId: OrganizationId, jobId: JobId): Rdf#URI =
      jobContainer(orgId).fragmentLess / jobId.id fragment "thing"

    def fromUri(uri: Rdf#URI): Validation[BananaException, (OrganizationId, JobId)] =
      r findFirstIn uri.getString match {
        case Some(r(orgId, jobId)) => Success(OrganizationId(orgId), JobId(jobId))
        case None => Failure(FailedConversion("JobUri.fromUri: " + uri.getString))
      }

    def toUri(t: (OrganizationId, JobId)): Rdf#URI = apply(t._1, t._2)
  }

//  implicit object JobDataUri
//  extends PrefixBuilder("", "https://validator.w3.org/suite/jobData/")(ops)
//  with URIBinder[Rdf, JobDataId] {
//    def apply(id: JobDataId): Rdf#URI = apply(id.toString)
//    def fromUri(uri: Rdf#URI): Validation[BananaException, JobDataId] = getLocalName(uri) map JobDataId.apply
//    def toUri(t: JobDataId): Rdf#URI = apply(t)
//  }

  val organizationContainer = URI("https://validator.w3.org/suite/organizations/")

  implicit object OrganizationUri extends URIBinder[Rdf, OrganizationId] {
    def apply(orgId: OrganizationId): Rdf#URI = organizationContainer / orgId.id fragment "thing"
    def fromUri(uri: Rdf#URI): Validation[BananaException, OrganizationId] = {
      val str = uri.getString
      if (str.startsWith(organizationContainer.getString) && str.endsWith("#thing"))
        Success(OrganizationId(str.substring(str.lastIndexOf('/') + 1, str.lastIndexOf('#'))))
      else
        Failure(WrongExpectation("cannot extract OrganizationId from " + str))
    }
    def toUri(t: OrganizationId): Rdf#URI = apply(t)
  }

//  implicit object ResourceResponseUri {
//
//    def fromUri(uri: Rdf#URI): Validation[BananaException, ResourceResponseId] =
//      for {
//        fragment <- uri.fragment toSuccess WrongExpectation(uri + " has no fragment")
//      } yield ResourceResponseId(fragment)
//
//    def toUri(runId: RunId, rrId: ResourceResponseId): Rdf#URI = RunUri.toUri(runId).fragment(rrId.toString)
//  }
//

  def runContainer(orgId: OrganizationId, jobId: JobId): Rdf#URI = JobUri(orgId, jobId).fragmentLess / "runs"

  implicit object RunUri extends URIBinder[Rdf, (OrganizationId, JobId, RunId)] {

    val r = new scala.util.matching.Regex("""https://validator.w3.org/suite/organizations/([^/]+?)/jobs/([^/]+?)/runs/([^/]+?)#thing""", "organizationId", "jobId", "runId")

    def apply(orgId: OrganizationId, jobId: JobId, runId: RunId): Rdf#URI =
      runContainer(orgId, jobId).fragmentLess / runId.id fragment "thing"

    def fromUri(uri: Rdf#URI): Validation[BananaException, (OrganizationId, JobId, RunId)] =
      r findFirstIn uri.getString match {
        case Some(r(orgId, jobId, runId)) => Success(OrganizationId(orgId), JobId(jobId), RunId(runId))
        case None => Failure(FailedConversion("RunUri.fromUri: " + uri.getString))
      }

    def toUri(t: (OrganizationId, JobId, RunId)): Rdf#URI = apply(t._1, t._2, t._3)
  }

  val userContainer: Rdf#URI = URI("https://validator.w3.org/suite/users/")

  implicit object UserUri extends URIBinder[Rdf, UserId] {
    def apply(userId: UserId): Rdf#URI = userContainer / userId.id fragment "me"
    def fromUri(uri: Rdf#URI): Validation[BananaException, UserId] = {
      val str = uri.getString
      if (str.startsWith(userContainer.getString) && str.endsWith("#me"))
        Success(UserId(str.substring(str.lastIndexOf('/') + 1, str.lastIndexOf('#'))))
      else
        Failure(WrongExpectation("cannot extract UserId from " + str))
    }

    def toUri(t: UserId): Rdf#URI = apply(t)
  }

//  implicit object AssertorResponseUri
//  extends PrefixBuilder("", "https://validator.w3.org/suite/assertorResponse/")(ops)
//  with URIBinder[Rdf, AssertorResponseId] {
//    def apply(id: AssertorResponseId): Rdf#URI = apply(id.toString)
//    def fromUri(uri: Rdf#URI): Validation[BananaException, AssertorResponseId] = getLocalName(uri) map AssertorResponseId.apply
//    def toUri(t: AssertorResponseId): Rdf#URI = apply(t)
//  }

  implicit object AssertorUri
  extends PrefixBuilder("", "https://validator.w3.org/suite/assertor/")(ops)
  with URIBinder[Rdf, AssertorId] {
    def apply(id: AssertorId): Rdf#URI = apply(id.toString)
    def fromUri(uri: Rdf#URI): Validation[BananaException, AssertorId] = getLocalName(uri) map AssertorId.apply
    def toUri(t: AssertorId): Rdf#URI = apply(t)
  }

}
