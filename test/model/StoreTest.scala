package org.w3.vs.store

import org.scalatest.{Filter => ScalaTestFilter, _}
import org.scalatest.matchers._
import scalaz._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util._
import org.w3.vs._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.exception._
import org.w3.vs.diesel._
import org.w3.vs.store.Binders._
import org.w3.vs.util._

abstract class StoreTest(
  nbUrlsPerAssertions: Int,
  severities: Map[AssertionSeverity, Int],
  nbHttpErrorsPerAssertions: Int,
  nbHttpResponsesPerAssertions: Int,
  nbJobDatas: Int)
extends WordSpec with MustMatchers with BeforeAndAfterAll with Inside {

  val nbAssertionsPerRunPerAssertor = nbUrlsPerAssertions * ( severities(Error) + severities(Warning) + severities(Info) ) /* nb of contexts */
  val nbAssertionsPerRun = 2 /* nb of assertors */ * nbAssertionsPerRunPerAssertor
  val nbAssertionsForJob1 = 2 /* runs */ * nbAssertionsPerRun

  implicit val conf: VSConfiguration = new DefaultTestConfiguration { }

  import conf._

  // used to remember the uri that was assigned to an object saved into the store
  val uriMap: collection.mutable.Map[AnyRef, Rdf#URI] = {
    import collection.JavaConverters._
    val identityMap = new java.util.IdentityHashMap[AnyRef, Rdf#URI]
    identityMap.asScala
  }

  // that's exactly why manipulating the id ourselves is a bad idea
  val orgId = OrganizationId()
  val user1Id = UserId()

  val org: Organization = Organization(orgId, "World Wide Web Consortium", user1Id)

  val user1: User = User(user1Id, "foo", "foo@example.com", "secret", Some(orgId))

  val user2 = User(UserId(), "bar", "bar@example.com", "secret", Some(org.id))
  
  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing))

  val strategy2 =
    Strategy( 
      entrypoint=URL("http://localhost:9001/foo"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val now = DateTime.now(DateTimeZone.UTC)

  val job1 = Job(
    id = JobId(),
    name = "job1",
    createdOn = now,
    strategy = strategy,
    creator = user1.id,
    organization = org.id)

  val job2 = Job(
    id = JobId(),
    name = "job2",
    createdOn = now,
    strategy = strategy,
    creator = user1.id,
    organization = org.id)

  val job3 = Job(
    id = JobId(),
    name = "job3",
    createdOn = now,
    strategy = strategy,
    creator = user1.id,
    organization = org.id)

  val job4 = Job(
    id = JobId(),
    name = "job4",
    createdOn = now,
    strategy = strategy2,
    creator = user2.id,
    organization = org.id)

  val job5 = Job(
    id = JobId(),
    name = "job5",
    createdOn = now,
    strategy = strategy,
    creator = user1.id,
    organization = OrganizationId())

  // a job may have never completed, for example if the user has forced a new run
  // is this assumption ok? -> yes
  // or do we want to force a completedAt before switching to the new Job? this would be weird
  var run1 = Run(id = (org.id, job1.id, RunId()), strategy = job1.strategy, createdAt = now)

  var run2 = Run(id = (org.id, job1.id, RunId()), strategy = job1.strategy, createdAt = now.plusMinutes(5)).completedAt(now.plusMinutes(7))

  var run3 = Run(id = (org.id, job1.id, RunId()), strategy = job1.strategy, createdAt = now.plusMinutes(10)).completedAt(now.plusMinutes(12))

  var run4 = Run(id = (org.id, job1.id, RunId()), strategy = job1.strategy, createdAt = now.plusMinutes(15))

  var run5 = Run(id = (org.id, job5.id, RunId()), strategy = job5.strategy, createdAt = now)

  val assertorIds = List("test-assertor-1", "test-assertor-2")

    def newAssertion(url: URL, assertor: String, severity: AssertionSeverity): Assertion = {
      val contexts = List(Context("blah", Some(42), None), Context("blarf", None, Some(42)))
      Assertion(
        url = url,
        assertor = assertor,
        contexts = contexts,
        lang = "fr",
        title = "some title",
        severity = severity,
        description = Some("some description"))
    }

  // these assertions are for job1, in run1
  def addAssertions(): Unit = {
    for {
//      runId <- List(run1.id, run2.id)
      assertorId <- assertorIds
      i <- 1 to nbUrlsPerAssertions
      severity <- List(Error, Warning, Info)
      nb = severities(severity)
      j <- 1 to nb
    } {
      val url = URL("http://example.com/foo/"+i)
      val assertion = newAssertion(url, assertorId, severity)
      val assertorResult = AssertorResult(run1.id, assertorId, url, List(assertion))
      Run.saveEvent(run1.runUri, AssertorResponseEvent(assertorResult)).await(3.seconds)
      run1 = run1.copy(assertions = run1.assertions + assertion)
    }
    Run.completedAt(job1.jobUri, run2.runUri, run2.completedAt.get).await(3.seconds)
    Run.completedAt(job1.jobUri, run3.runUri, run3.completedAt.get).await(3.seconds)
  }

//   val resourceResponses: Vector[ResourceResponse] = {
//     val builder = Vector.newBuilder[ResourceResponse]
//     builder.sizeHint(nbHttpErrorsPerAssertions + nbHttpResponsesPerAssertions)
//     for ( i <- 1 to nbHttpErrorsPerAssertions)
//       builder += ErrorResponse(
//         jobId = job1,
//         runId = run1,
//         url = URL("http://example.com/error/" + i),
//         action = GET,
//         why = "because I can")
//     for ( i <- 1 to nbHttpResponsesPerAssertions)
//       builder += HttpResponse(
//         jobId = job1,
//         runId = run1,
//         url = URL("http://example.com/foo/" + i),
//         action = GET,
//         status = 200,
//         headers = Map("foo" -> List("bar")),
//         extractedURLs = List(URL("http://example.com/foo/"+i+"/1"), URL("http://example.com/foo/"+i+"/2")))
//     builder.result()
//   }





  override def beforeAll(): Unit = {
    val start = System.currentTimeMillis
    val r = for {
      _ <- Organization.save(org)
      _ <- User.save(user1)
      _ <- User.save(user2)
      _ <- Job.save(job1)
      _ <- Job.save(job2)
      _ <- Job.save(job3)
      _ <- Job.save(job4)
      _ <- Job.save(job5)
      _ <- Run.save(run1)
      _ <- Run.save(run2)
      _ <- Run.save(run3)
      _ <- Run.save(run4)
      _ <- Run.save(run5)
    } yield ()
    r.await(10.second)
    addAssertions() // <- already blocking

    val end = System.currentTimeMillis
    val durationInSeconds = (end - start) / 1000.0
    println("DEBUG: it took about " + durationInSeconds + " seconds to load all the entities for this test")
  }

  "retrieve unknown Job" in {
    val retrieved = Job.get(OrganizationId(), JobId()).result(1.second)
    retrieved must be ('Failure) // TODO test exception type (UnknownJob)
  }
  
  "retrieve unknown Organization" in {
    val retrieved = Organization.get(organizationContainer / "foo").result(1.second)
    retrieved must be ('Failure) // TODO test exception type (UnknownOrganization)
  }
 
  "create, put, retrieve, delete Job" in {
    val job = job1.copy(id = JobId())
    Job.get(org.id, job.id).result(1.second) must be ('failure)
    Job.save(job).result(1.second) must be ('success)
    val retrieved = Job.get(org.id, job.id).getOrFail(10.second)._1
    retrieved must be (job)
    Job.delete(job).result(1.second) must be ('success)
    Job.get(org.id, job.id).result(1.second) must be ('failure)
  }

  "retrieve Organization" in {
    val retrieved = Organization.get(org.id).getOrFail(3.seconds)
    retrieved must be(org)
  }

  "save and retrieve User" in {
    val retrieved = User.get(user1.id).getOrFail(3.seconds)
    retrieved must be(user1)
  }

  "retrieve User by email" in {
    User.getByEmail("foo@example.com").getOrFail(3.seconds) must be(user1)

    User.getByEmail("unknown@example.com").result(1.second) must be === (Failure(UnknownUser))
  }

  "authenticate a user" in {
    User.authenticate("foo@example.com", "secret").result(1.second) must be === (Success(user1))

    User.authenticate("foo@example.com", "bouleshit").result(1.second) must be === (Failure(Unauthenticated))

    User.authenticate("unknown@example.com", "bouleshit").result(1.second) must be === (Failure(UnknownUser))
  }

  "get all Jobs that belong to the same organization" in {
    val jobs = Job.getFor(org.id).getOrFail(3.seconds)
    jobs must have size(4)
    jobs must contain (job1)
    jobs must contain (job2)
    jobs must contain (job3)
    jobs must contain (job4)
  }

  "a user can only access the jobs that he created" in {
    val jobs = Job.getFor(user1.id).getOrFail(3.seconds)
    jobs must have size(4)
    jobs must contain (job1)
    jobs must contain (job2)
    jobs must contain (job3)
    jobs must contain (job5)
  }

  "retrieve Run" in {
    val run = Run.get(run1.runUri).getOrFail(10.seconds)._1
    run.assertions.size must be(run1.assertions.size)
  }

//  "get history of JobDatas for a given jobId" in {
//    // define test logic
//  }

  "get timestamp of latest completed Run for a given job" in {
    val latestCompleted = job1.getLastCompleted().getOrFail(3.seconds)
    latestCompleted must be (run3.completedAt)
  }

  "get timestamp for a job that has never been completed once" in {
    val neverCompleted = job2.getLastCompleted().getOrFail(3.seconds)
    neverCompleted must be(None)
  }

}


class StoreTestSuperLight extends StoreTest(
  nbUrlsPerAssertions = 1,
  severities = Map(Error -> 1, Warning -> 0, Info -> 0),
  nbHttpErrorsPerAssertions = 1,
  nbHttpResponsesPerAssertions = 1,
  nbJobDatas = 1)

class StoreTestLight extends StoreTest(
  nbUrlsPerAssertions = 10,
  severities = Map(Error -> 2, Warning -> 3, Info -> 4),
  nbHttpErrorsPerAssertions = 2,
  nbHttpResponsesPerAssertions = 5,
  nbJobDatas = 3)

abstract class StoreTestHeavy extends StoreTest(
  nbUrlsPerAssertions = 100,
  severities = Map(Error -> 10, Warning -> 10, Info -> 10),
  nbHttpErrorsPerAssertions = 5,
  nbHttpResponsesPerAssertions = 10,
  nbJobDatas = 3)
