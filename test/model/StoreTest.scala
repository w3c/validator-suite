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

class StoreTest extends WordSpec with MustMatchers with BeforeAndAfterAll {

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

  val run2 = Run(job = job2)

  val assertorId = AssertorId()

  val assertion1 = Assertion(
    jobId = job1.id,
    runId = run1.id,
    assertorId = assertorId,
    url = URL("http://example.com/foo"),
    lang = "fr",
    title = "foo",
    severity = Error,
    description = Some("some description"))

  val assertion2 = Assertion(
    jobId = job1.id,
    runId = run1.id,
    assertorId = assertorId,
    url = URL("http://example.com/bar"),
    lang = "fr",
    title = "bar",
    severity = Warning,
    description = Some("some other description"))

  val context1 = Context(
    content = "blah",
    line = Some(42),
    column = None,
    assertionId = assertion1.id)

  val context2 = Context(
    content = "blah",
    line = None,
    column = Some(42),
    assertionId = assertion1.id)

  val context3 = Context(
    content = "blah",
    line = Some(42),
    column = None,
    assertionId = assertion2.id)

  val context4 = Context(
    content = "blah",
    line = None,
    column = Some(42),
    assertionId = assertion2.id)

  override def beforeAll(): Unit = {
    Organization.save(org)
    User.save(user)
    User.save(user2)
    Job.save(job1)
    Job.save(job2)
    Job.save(job3)
    Job.save(job4)
    Run.save(run1)
    Run.save(run2)
    Assertion.save(assertion1)
    Assertion.save(assertion2)
    Context.save(context1)
    Context.save(context2)
    Context.save(context3)
    Context.save(context4)
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
    val retrieved = Job.get(job1.id).result(1.second)
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
    val retrieved = Assertion.get(assertion1.id).result(1.second)
    retrieved must be === (Success(assertion1))
  }

  "retrieve Context" in {
    val retrieved = Context.get(context1.id).result(1.second)
    retrieved must be === (Success(context1))
  }

  "get all assertions for a given a runId" in {
    val assertions = Assertion.getForRun(run1.id).result(1.second) getOrElse sys.error("")
    assertions must have size(2)
    assertions must contain (assertion1)
    assertions must contain (assertion2)

  }

}
