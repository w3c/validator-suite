package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import org.w3.banana._
import scalaz.Scalaz._
import scalaz._
import org.w3.banana._
import org.w3.banana.util._
import org.w3.banana.LinkedDataStore._
import org.w3.vs._
import diesel._
import org.w3.vs.store.Binders._
import org.w3.vs.sparql._
import org.w3.banana.util._

case class Assertion(
    url: URL,
    assertorId: AssertorId,
    contexts: List[Context],
    lang: String,
    title: String,
    severity: AssertionSeverity,
    description: Option[String],
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC))

object Assertion {

  def bananaSave(orgId: OrganizationId, jobId: JobId, runId: RunId, assertion: Assertion)(implicit conf: VSConfiguration): BananaFuture[Unit] =
    bananaSave((orgId, jobId, runId).toUri, assertion)

  def bananaSave(runUri: Rdf#URI, assertion: Assertion)(implicit conf: VSConfiguration): BananaFuture[Unit] = {
    import conf._
    store.append(runUri, runUri -- ont.assertion ->- assertion.toPG)
  }

  def save(orgId: OrganizationId, jobId: JobId, runId: RunId, assertion: Assertion)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    save((orgId, jobId, runId).toUri, assertion)

  def save(runUri: Rdf#URI, assertion: Assertion)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    bananaSave(runUri, assertion).toFutureVal


}
