package org.w3.vs.store

import org.scalatest.{Filter => ScalaTestFilter, _}
import org.scalatest.matchers._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util._
import org.w3.vs._
import org.w3.vs.model._
import org.w3.vs.exception._
import org.w3.vs.util._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import scala.concurrent.duration.Duration
import org.w3.util.Util._
import akka.actor.ActorPath

abstract class MongoStoreTest(
  nbUrlsPerAssertions: Int,
  severities: Map[AssertionSeverity, Int],
  nbHttpErrorsPerAssertions: Int,
  nbHttpResponsesPerAssertions: Int,
  nbRunDatas: Int)
extends WordSpec with MustMatchers with BeforeAndAfterAll with Inside {

  val nbAssertionsPerRunPerAssertor = nbUrlsPerAssertions * ( severities(Error) + severities(Warning) + severities(Info) ) /* nb of contexts */
  val nbAssertionsPerRun = 2 /* nb of assertors */ * nbAssertionsPerRunPerAssertor
  val nbAssertionsForJob1 = 2 /* runs */ * nbAssertionsPerRun

  implicit val conf: VSConfiguration = new DefaultTestConfiguration { }

  import conf._

  // just for the sake of this test :-)
  val actorPath = ActorPath.fromString("akka://system/user/foo")

  val user1: User = User.create("foo", "foo@example.com", "secret", isSubscriber = true)

  val user2 = User.create("bar", "bar@example.com", "secret", isSubscriber = true)

  val user3 = User.create("baz", "baz@example.com", "secret", isSubscriber = true)
  
  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = AssertorsConfiguration.default)

  val strategy2 =
    Strategy( 
      entrypoint=URL("http://localhost:9001/foo"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = AssertorsConfiguration.default)
  
  val now = DateTime.now(DateTimeZone.UTC)

  var job1 = Job(
    id = JobId(),
    name = "job1",
    createdOn = now,
    strategy = strategy,
    creatorId = user1.id,
    status = NeverStarted,
    latestDone = None)

  val job2 = Job(
    id = JobId(),
    name = "job2",
    createdOn = now,
    strategy = strategy,
    creatorId = user1.id,
    status = NeverStarted,
    latestDone = None)

  val job3 = Job(
    id = JobId(),
    name = "job3",
    createdOn = now,
    strategy = strategy,
    creatorId = user1.id,
    status = NeverStarted,
    latestDone = None)

  val job4 = Job(
    id = JobId(),
    name = "job4",
    createdOn = now,
    strategy = strategy2,
    creatorId = user2.id,
    status = NeverStarted,
    latestDone = None)

  val run5Id = RunId()

  val job5 = Job(
    id = JobId(),
    name = "job5",
    createdOn = now,
    strategy = strategy,
    creatorId = user1.id,
    status = Running(run5Id, actorPath),
    latestDone = None)

  // a job may have never completed, for example if the user has forced a new run
  var run1 = Run(RunId(), job1.strategy)

  var run2 = Run(RunId(), job1.strategy).completeOn(now.plusMinutes(7))

  var run3 = Run(RunId(), job1.strategy).completeOn(now.plusMinutes(12))

  var run4 = Run(RunId(), job1.strategy)

  var run5: Run = Run(run5Id, job5.strategy)

  val assertorIds = List(AssertorId("test_assertor_1"), AssertorId("test_assertor_2"))

  def newAssertion(url: URL, assertor: AssertorId, severity: AssertionSeverity): Assertion = {
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
      assertorId <- assertorIds
      i <- 1 to nbUrlsPerAssertions
    } {
      // Only one AssertorResult for every unique (assertor, url). No partial validations.
      val url = URL("http://example.com/foo/"+java.util.UUID.randomUUID().toString)
      val assertions = for {
        severity <- List(Error, Warning, Info)
        nb = severities(severity)
        j <- 1 to nb
      } yield {
        val assertion = newAssertion(url, assertorId, severity)
        run1 = run1.copy(assertions = run1.assertions + assertion)
        assertion
      }
      val assertorResult = AssertorResult(run1.runId, assertorId, url, assertions)
      Run.saveEvent(AssertorResponseEvent(user1.id, job1.id, run1.runId, assertorResult)).getOrFail()
    }
  }


  override def beforeAll(): Unit = {
    val start = System.currentTimeMillis
    val initScript = for {
      _ <- MongoStore.reInitializeDb()
      _ <- User.save(user1)
      _ <- User.save(user2)
      _ <- User.save(user3)
      _ <- Job.save(job1)
      _ <- Job.save(job2)
      _ <- Job.save(job3)
      _ <- Job.save(job4)
      _ <- Job.save(job5)
      _ <- Run.saveEvent(CreateRunEvent(user1.id, job1.id, run1.runId, actorPath, run1.strategy, now))
      _ <- Run.saveEvent(CreateRunEvent(user1.id, job1.id, run2.runId, actorPath, run2.strategy, now))
      _ <- Run.saveEvent(CreateRunEvent(user1.id, job1.id, run3.runId, actorPath, run3.strategy, now))
      _ <- Run.saveEvent(CreateRunEvent(user1.id, job1.id, run4.runId, actorPath, run4.strategy, now))
      _ <- Run.saveEvent(CreateRunEvent(user1.id, job5.id, run5.runId, actorPath, run5.strategy, now))
    } yield ()
    initScript.getOrFail()
    addAssertions() // <- already blocking
    Run.saveEvent(CompleteRunEvent(user1.id, job1.id, run2.runId, run2.data, run2.resourceDatas, run2.completedOn.get)).getOrFail()
    Run.saveEvent(CompleteRunEvent(user1.id, job1.id, run3.runId, run3.data, run3.resourceDatas, run3.completedOn.get)).getOrFail()
    /* job1 is still running with run4, and lastestDone was run3 */
    val status = Running(run4.runId, akka.actor.ActorPath.fromString("akka://system/user/foo"))
    val latestDone = Done(run4.runId, Completed, run3.completedOn.get, run3.data)
    Job.updateStatus(
      job1.id,
      status = status,
      latestDone = latestDone).getOrFail()
    job1 = job1.copy(status = status, latestDone = Some(latestDone))
    /****/
    val end = System.currentTimeMillis
    val durationInSeconds = (end - start) / 1000.0
    println("DEBUG: it took about " + durationInSeconds + " seconds to load all the entities for this test")
  }

  override def afterAll(): Unit = {
    connection.close()
    httpClient.close()
    system.shutdown()
    system.awaitTermination()
  }

  "User" in {
    val r = User.get(user1.id).getOrFail()
    r must be(user1)
  }

  "retrieve User by email" in {
    User.getByEmail("foo@example.com").getOrFail(3.seconds) must be(user1)

    Try { User.getByEmail("unknown@example.com").getOrFail() } must be (Failure(UnknownUser))
  }

  "a User can't have an email already in use" in {
    val user = User.create("FOO", "foo@example.com", "secret", isSubscriber = true)
    Try { User.save(user).getOrFail() } must be (Failure(DuplicatedEmail("foo@example.com")))
    Try { User.register("FOO", "foo@example.com", "secret", true).getOrFail() } must be (Failure(DuplicatedEmail("foo@example.com")))
  }

  "authenticate a user" in {
    Try { User.authenticate("foo@example.com", "secret").getOrFail() } must be (Success(user1))

    Try { User.authenticate("foo@example.com", "bouleshit").getOrFail() } must be (Failure(Unauthenticated))

    Try {User.authenticate("unknown@example.com", "bouleshit").getOrFail() } must be (Failure(UnknownUser))
  }

  "retrieve unknown Job" in {
    val retrieved = Try { Job.get(JobId()).getOrFail() }
    retrieved must be ('Failure) // TODO test exception type (UnknownJob)
  }

  "create, put, retrieve, delete Job" in {
    val job = job1.copy(id = JobId())
    Try { Job.get(job.id).getOrFail() } must be ('failure)
    Try { Job.save(job).getOrFail() } must be ('success)
    val retrieved = Job.get(job.id).getOrFail(10.seconds)
    retrieved must be (job)
    Try { Job.delete(job.id).getOrFail() } must be ('success)
    Try { Job.get(job.id).getOrFail() } must be ('failure)
  }

  "a user can only access the jobs that he created" in {
    val jobs = Job.getFor(user1.id).getOrFail(3.seconds)
    jobs must have size(4)
    jobs must contain (job1)
    jobs must contain (job2)
    jobs must contain (job3)
    jobs must contain (job5)
  }

  "a user with no job should still be able to list his empty list of jobs" in {
    val jobs = Job.getFor(user3.id).getOrFail(3.seconds)
    jobs must be ('empty)
  }

  "retrieve Run" in {
    val run = Run.get(run1.runId).getOrFail(10.seconds)._1
    run.assertions.size must be(run1.assertions.size)
  }

  "get all assertions for a run timestamp of latest completed Run for a given job" in {
    val assertions = Run.getAssertions(run1.runId).getOrFail()
    assertions must have length(nbAssertionsPerRun)
  }

  "get all running jobs" in {
    val runningJobs = Job.getRunningJobs().getOrFail()
    runningJobs must have size(2)
    runningJobs must contain(job1)
    runningJobs must contain(job5)
  }

  "reInitialize a job" in {
    val jobId = JobId()
    val runId = RunId()
    val job = job1.copy(id = jobId, status = Running(runId, akka.actor.ActorPath.fromString("akka://123456")))
    val run = run1.copy(runId = runId)
    val url = URL("http://example.com/foo")
    val assertion = Assertion(
      url = url,
      assertor = AssertorId("foo"),
      contexts = List.empty,
      lang = "fr",
      title = "some title",
      severity = Warning,
      description = Some("some description"))
    val assertorResult = AssertorResult(run.runId, AssertorId("foo"), url, List(assertion))
    val script = for {
      _ <- Job.save(job)
      _ <- Run.saveEvent(CreateRunEvent(user1.id, job1.id, run.runId, actorPath, run.strategy, now))
      _ <- Run.saveEvent(AssertorResponseEvent(user1.id, job1.id, run.runId, assertorResult))
      assertionsBefore <- Run.getAssertions(run.runId)
      _ <- Job.reset(job.id, removeRunData = true)
      rebornJob <- Job.get(job.id)
      assertionsAfter <- Run.getAssertions(run.runId)
    } yield {
      rebornJob.id must be(job.id)
      rebornJob.status must be(NeverStarted)
      rebornJob.latestDone must be(None)
      assertionsBefore must have size(1)
      assertionsAfter must be('empty)
    }
    script.getOrFail()
  }

}

class MongoStoreTestLight extends MongoStoreTest(
  nbUrlsPerAssertions = 10,
  severities = Map(Error -> 2, Warning -> 3, Info -> 4),
  nbHttpErrorsPerAssertions = 2,
  nbHttpResponsesPerAssertions = 5,
  nbRunDatas = 3)
