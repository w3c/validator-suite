package org.w3.vs.store

import org.scalatest.{Filter => ScalaTestFilter, _}
import org.scalatest.matchers._
import scalaz._
import org.w3.banana._
import org.w3.banana.jena._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.URL
import org.w3.vs._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.exception._

abstract class StoreTest(
  nbUrlsPerAssertions: Int,
  nbErrors: Int,
  nbWarnings: Int,
  nbInfos: Int,
  nbHttpErrorsPerAssertions: Int,
  nbHttpResponsesPerAssertions: Int,
  nbJobDatas: Int)
extends WordSpec with MustMatchers with BeforeAndAfterAll {

  val nbAssertionsPerRun = nbUrlsPerAssertions * ( nbErrors + nbWarnings + nbInfos )
  val nbAssertionsForJob1 = 2 /* runs */ * nbAssertionsPerRun

  implicit val conf: VSConfiguration = new DefaultProdConfiguration { }

  val org = Organization(id = OrganizationId(), name = "World Wide Web Consortium", adminId = UserId())

  val user = User(UserId(), "foo", "foo@example.com", "secret", org.id)

  val user2 = User(UserId(), "bar", "bar@example.com", "secret", org.id)
  
  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing)) //.noAssertor()

  val strategy2 =
    Strategy( 
      entrypoint=URL("http://localhost:9001/foo"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing)) //.noAssertor()
  
  val job1 = Job(
    strategy = strategy,
    creatorId = user.id,
    organizationId = org.id,
    name = "job1")

  val job2 = Job(
    strategy = strategy,
    creatorId = user.id,
    organizationId = org.id,
    name = "job2")

  val job3 = Job(
    strategy = strategy,
    creatorId = user.id,
    organizationId = org.id,
    name = "job3")

  val job4 = Job(
    strategy = strategy2,
    creatorId = user2.id,
    organizationId = org.id,
    name = "job4")

  val run1 = Run(job = job1)

  val run2 = Run(job = job1)

  val assertorId = AssertorId()

  // assertions for job1
  val assertions: Vector[Assertion] = {
    def newAssertion(runId: RunId, url: URL, severity: AssertionSeverity): Assertion =
      Assertion(
        jobId = job1.id,
        runId = runId,
        assertorId = assertorId,
        url = url,
        lang = "fr",
        title = "some title",
        severity = severity,
        description = Some("some description"))
    val builder = Vector.newBuilder[Assertion]
    builder.sizeHint(nbAssertionsForJob1)
    for ( runId <- List(run1.id, run2.id) ; i <- 1 to nbUrlsPerAssertions ) {
      for ( j <- 1 to nbErrors ) builder += newAssertion(runId, URL("http://example.com/foo/"+i), Error)
      for ( j <- 1 to nbWarnings ) builder += newAssertion(runId, URL("http://example.com/foo/"+i), Warning)
      for ( j <- 1 to nbInfos ) builder += newAssertion(runId, URL("http://example.com/foo/"+i), Info)
    }
    builder.result()
  }

  // contexts for all the assertions in job1
  val contexts: Vector[Context] = {
    val builder = Vector.newBuilder[Context]
    builder.sizeHint(2 * nbAssertionsForJob1)
    for ( assertion <- assertions ) {
      builder += Context(
        content = "blah",
        line = Some(42),
        column = None,
        assertionId = assertion.id)
      builder += Context(
        content = "blah",
        line = None,
        column = Some(42),
        assertionId = assertion.id)
    }
    builder.result()
  }

  val resourceResponses: Vector[ResourceResponse] = {
    val builder = Vector.newBuilder[ResourceResponse]
    builder.sizeHint(nbHttpErrorsPerAssertions + nbHttpResponsesPerAssertions)
    for ( i <- 1 to nbHttpErrorsPerAssertions)
      builder += ErrorResponse(
        jobId = job1,
        runId = run1,
        url = URL("http://example.com/error/" + i),
        action = GET,
        why = "because I can")
    for ( i <- 1 to nbHttpResponsesPerAssertions)
      builder += HttpResponse(
        jobId = job1,
        runId = run1,
        url = URL("http://example.com/foo/" + i),
        action = GET,
        status = 200,
        headers = Map("foo" -> List("bar")),
        extractedURLs = List(URL("http://example.com/foo/"+i+"/1"), URL("http://example.com/foo/"+i+"/2")))
    builder.result()
  }

  // jobdatas for run1
  val jobDatas: Vector[JobData] = {
    val now = DateTime.now(DateTimeZone.UTC)
    val builder = Vector.newBuilder[JobData]
    builder.sizeHint(nbJobDatas)
    for (i <- 1 to nbJobDatas)
      builder += JobData(
        id = JobDataId(),
        runId = run1.id,
        resources = 1000,
        errors = 42,
        warnings = 1,
        timestamp = now.plusSeconds(2))
    builder.result()
  }

  override def beforeAll(): Unit = {
    val start = System.currentTimeMillis
    (for {
      _ <- Organization.save(org)
      _ <- User.save(user)
      _ <- User.save(user2)
      _ <- Job.save(job1)
      _ <- Job.save(job2)
      _ <- Job.save(job3)
      _ <- Job.save(job4)
      _ <- Run.save(run1)
      _ <- Run.save(run2)
    } yield ()).result(1.second)
    assertions foreach { assertion => Assertion.save(assertion).result(10.milliseconds) }
    contexts foreach { context => Context.save(context).result(10.milliseconds) }
    resourceResponses foreach { rr => ResourceResponse.save(rr).result(10.milliseconds) }
    jobDatas foreach { jd => JobData.save(jd).result(10.milliseconds) }
    val end = System.currentTimeMillis
    val durationInSeconds = (end - start) / 1000.0
    println("DEBUG: it took about " + durationInSeconds + " seconds to load all the entities for this test")
  }

  "retrieve unknown Job" in {
    val retrieved = Job.get(JobId()).result(1.second)
    retrieved must be ('Failure) // TODO test exception type (UnknownJob)
  }
  
  "retrieve unknown Organization" in {
    val retrieved = Organization.get(OrganizationId()).result(1.second)
    retrieved must be ('Failure) // TODO test exception type (UnknownOrganization)
  }
  
  "retrieve Job" in {
    val retrieved = Job.get(job1.id).result(10.second)
    retrieved must be === (Success(job1))
  }

  "retrieve Organization" in {
    val retrieved = Organization.get(org.id).result(1.second)
    retrieved must be === (Success(org))
  }
  
  // why is that throwing exceptions in the logs?
  // also it's very slow...
  // "retrieve run" in {
  //   // Doesn't really have to do anything in that file. Useful for current debug
  //   val orgId = OrganizationId()
  //   val job = job1.copy(id = JobId(), organizationId = orgId)
  //   val org1 = org.copy(id = orgId)
  //   job.getRun.result(1.second) must be ('Failure)
  //   org1.save()
  //   job.getRun.result(1.second) must be ('Failure)
  //   job.save()
  //   job.getRun.result(1.second) must be ('Success)
  // }

  "save and retrieve User" in {
    val retrieved = User.get(user.id).result(1.second)
    retrieved must be === (Success(user))
  }

  "retrieve User by email" in {
    User.getByEmail("foo@example.com").result(1.second) must be === (Success(user))

    User.getByEmail("unknown@example.com").result(1.second) must be === (Failure(UnknownUser))
  }

  "authenticate a user" in {
    User.authenticate("foo@example.com", "secret").result(1.second) must be === (Success(user))

    User.authenticate("foo@example.com", "bouleshit").result(1.second) must be === (Failure(Unauthenticated))

    User.authenticate("unknown@example.com", "bouleshit").result(1.second) must be === (Failure(UnknownUser))
  }

  "get all Jobs given one creator" in {
    val jobs = Job.getFor(strategy.id).result(1.second) getOrElse sys.error("")
    jobs must have size(3)
    jobs must contain (job1)
    jobs must contain (job2)
    jobs must contain (job3)
  }

  "get all Jobs sharing the same strategy" in {
    val jobs = Job.getFor(strategy.id).result(1.second) getOrElse sys.error("")
    jobs must have size(3)
    jobs must contain (job1)
    jobs must contain (job2)
    jobs must contain (job3)
  }

  "get all Jobs that belong to the same organization" in {
    val jobs = Job.getFor(org.id).result(1.second) getOrElse sys.error("")
    jobs must have size(4)
    jobs must contain (job1)
    jobs must contain (job2)
    jobs must contain (job3)
    jobs must contain (job4)
  }

  "get all Jobs a user can access through all the organizations he belongs to" in {
    val jobs = Job.getFor(user).result(1.second) getOrElse sys.error("")
    jobs must have size(4)
    jobs must contain (job1)
    jobs must contain (job2)
    jobs must contain (job3)
    jobs must contain (job4)
  }

  "retrieve Run" in {
    val retrieved = Run.get(run1.id).result(1.second)
    retrieved must be === (Success(run1))
  }

  "retrieve Assertion" in {
    val assertion = assertions(0)
    val retrieved = Assertion.get(assertion.id).result(1.second)
    retrieved must be === (Success(assertion))
  }

  "retrieve Context" in {
    val context = contexts(0)
    val retrieved = Context.get(context.id).result(1.second)
    retrieved must be === (Success(context))
  }

  "get all assertions for a given a runId" in {
    val retrievedAssertions = Assertion.getForRun(run1.id).result(2.second) getOrElse sys.error("")
    retrievedAssertions must have size(nbAssertionsPerRun)
    retrievedAssertions must contain (assertions(0))
  }

  // Wrong test, Assertion.getForJob(job1.id) must be equivalent to Assertion.getForRun(run1.id) if run1.id is the last run.
  // It's currently implicit but could be exposed by adding a runIdOption to Assertion.getForJob's signature
  "get all assertions for a given a jobId" in {
    val retrievedAssertions = Assertion.getForJob(job1.id).result(2.second) getOrElse sys.error("")
    retrievedAssertions must have size(nbAssertionsForJob1)
    retrievedAssertions must contain (assertions(0))
   }

  "get all URLArticles (don't know what it means :-) from a job" in {
    val urlArticles = job1.getURLArticles.result(2.seconds) getOrElse sys.error("")
    urlArticles must have size(nbUrlsPerAssertions)
  }

  "retrieve ResourceResponse" in {
    val rr = resourceResponses(0)
    ResourceResponse.get(rr.id).result(1.second) must be === (Success(rr))
  }

  "get all resources for a given a runId" in {
    val rrs = ResourceResponse.getForRun(run1.id).result(2.second) getOrElse sys.error("fooooo")
    rrs must have size (nbHttpErrorsPerAssertions + nbHttpResponsesPerAssertions)
    rrs must contain (resourceResponses(0))
   }

  "retrieve all context for a given assertionId" in {
    val assertion = assertions(0)
    val retrieved = Context.getForAssertion(assertion.id).result(1.second) getOrElse sys.error("test getForAssertion")
    retrieved must have size (2)
  }

  "save and retrieve JobData" in {
    val jobData = jobDatas(0)
    val retrieved = JobData.get(jobData.id).result(1.second)
    retrieved must be === (Success(jobData))
  }


}


class StoreTestLight extends StoreTest(
  nbUrlsPerAssertions = 10,
  nbErrors = 3,
  nbWarnings = 3,
  nbInfos = 3,
  nbHttpErrorsPerAssertions = 2,
  nbHttpResponsesPerAssertions = 5,
  nbJobDatas = 3)

class StoreTestHeavy extends StoreTest(
  nbUrlsPerAssertions = 100,
  nbErrors = 10,
  nbWarnings = 10,
  nbInfos = 10,
  nbHttpErrorsPerAssertions = 5,
  nbHttpResponsesPerAssertions = 10,
  nbJobDatas = 3)
