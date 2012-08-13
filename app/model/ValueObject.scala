package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.vs._

sealed trait ValueObject

case class JobVO (
    name: String,
    createdOn: DateTime,
    strategy: Strategy,
    creator: UserId,
    organization: OrganizationId) extends ValueObject
    
case class OrganizationVO (
  name: String,
  admin: UserId) extends ValueObject
    
case class RunVO (
    context: (OrganizationId, JobId),
    explorationMode: ExplorationMode = ProActive,
    // when the underlying Run was created/started
    createdAt: DateTime,
    assertions: Set[Assertion] = Set.empty,
    // when the underlying Run reached Idle for the last time
    // it's None if it has never been there
    completedAt: Option[DateTime],
    // when this RunVO was persisted
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC),
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0) extends ValueObject
    
case class UserVO(
    name: String,
    email: String,
    password: String,
    organization: Option[OrganizationId]) extends ValueObject
