package org.w3.vs.model

import org.scalatest._
import org.scalatest.matchers._
import scalaz._
import org.w3.banana._
import org.w3.banana.jena._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.URL
import org.w3.vs._
import akka.util.duration._

abstract class StoreTest(
  nbUrlsPerAssertions: Int,
  nbErrors: Int,
  nbWarnings: Int,
  nbInfos: Int)
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
  // we just create 2 contexts per assertion
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
    val end = System.currentTimeMillis
    val durationInSeconds = (end - start) / 1000.0
    println("!!!! it took about " + durationInSeconds + " seconds to load about " + (3 * nbAssertionsForJob1) + " entities")
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
    val retrieved = User.getByEmail("foo@example.com").result(1.second)
    retrieved must be === (Success(user))
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

  "get all assertions for a given a jobId" in {
    val retrievedAssertions = Assertion.getForJob(job1.id).result(10.second) getOrElse sys.error("")
    retrievedAssertions must have size(nbAssertionsForJob1)
    retrievedAssertions must contain (assertions(0))
   }

}


class StoreTestLight extends StoreTest(
  nbUrlsPerAssertions = 10,
  nbErrors = 3,
  nbWarnings = 3,
  nbInfos = 3)

class StoreTestHeavy extends StoreTest(
  nbUrlsPerAssertions = 100,
  nbErrors = 10,
  nbWarnings = 10,
  nbInfos = 10)
