package org.w3.vs.model

import org.w3.util._
import org.joda.time._

sealed trait ValueObject

case class AssertionVO (
    id: AssertionId = AssertionId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    url: URL,
    lang: String,
    title: String,
    severity: AssertionSeverity,
    description: Option[String],
    timestamp: DateTime) extends ValueObject

case class ContextVO (
    id: ContextId = ContextId(),
    content: String,
    line: Option[Int],
    column: Option[Int],
    assertionId: AssertionId) extends ValueObject

/*case class AssertorResultVO (
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime)*/

case class JobVO (
    id: JobId = JobId(),
    name: String,
    createdOn: DateTime,
    creatorId: UserId,
    organizationId: OrganizationId,
    strategyId: StrategyId) extends ValueObject
    
case class OrganizationVO (
    id: OrganizationId = OrganizationId(),
    name: String,
    admin: UserId) extends ValueObject
    
sealed trait ResourceResponseVO extends ValueObject {
  val id: ResourceResponseId
  val jobId: JobId
  val runId: RunId
  val url: URL
  val action: HttpAction
  val timestamp: DateTime
}

case class ErrorResponseVO (
    id: ResourceResponseId = ResourceResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime,
    why: String) extends ResourceResponseVO
    
case class HttpResponseVO (
    id: ResourceResponseId = ResourceResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime,
    status: Int,
    headers: Headers,
    extractedURLs: List[URL]) extends ResourceResponseVO
    
case class RunVO (
    id: RunId = RunId(),
    // to be removed
    jobId: JobId,
    explorationMode: ExplorationMode = ProActive,
    // when the underlying Run was created/started
    createdAt: DateTime,
    // when the underlying Run reached Idle for the last time
    // it's None if it has never been there
    completedAt: Option[DateTime],
    // when this RunVO was persisted
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC),
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0) extends ValueObject
    
case class StrategyVO (
    id: StrategyId = StrategyId(),
    entrypoint: URL,
    linkCheck: Boolean,
    maxResources: Int,
    filter: Filter,
    assertorSelector: AssertorSelector) extends ValueObject
    
case class UserVO (
    id: UserId = UserId(),
    name: String,
    email: String,
    password: String,
    organizationId: OrganizationId) extends ValueObject
