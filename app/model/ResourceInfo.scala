package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import java.util.UUID

case class ResourceInfo(
    id: ResourceInfo#Id = UUID.randomUUID(),
    url: URL,
    jobId: JobConfiguration#Id,
    action: HttpVerb,
    timestamp: DateTime = new DateTime,
    distancefromSeed: Int,
    result: ResourceInfoResult) {
  type Id = UUID
  
  def toTinyString: String = """[%s %s <%s> %d""" format (jobId.toString.substring(0, 6), action.toString, url.toString, distancefromSeed)
  
}



sealed trait ResourceInfoResult

case class ResourceInfoError(why: String) extends ResourceInfoResult

case class Fetch(
    status: Int,
    headers: Headers,
    extractedLinks: List[URL]) extends ResourceInfoResult

