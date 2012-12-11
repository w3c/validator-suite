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

abstract class MongoStoreTest(
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

  val user1: User = User(UserId(), "foo", "foo@example.com", "secret", isSubscriber = true)

  val user2 = User(UserId(), "bar", "bar@example.com", "secret", isSubscriber = true)

  val user3 = User(UserId(), "baz", "baz@example.com", "secret", isSubscriber = true)
  
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

  val job1 = Job(
    id = JobId(),
    name = "job1",
    createdOn = now,
    strategy = strategy,
    creator = user1.id)

  val job2 = Job(
    id = JobId(),
    name = "job2",
    createdOn = now,
    strategy = strategy,
    creator = user1.id)

  val job3 = Job(
    id = JobId(),
    name = "job3",
    createdOn = now,
    strategy = strategy,
    creator = user1.id)

  val job4 = Job(
    id = JobId(),
    name = "job4",
    createdOn = now,
    strategy = strategy2,
    creator = user2.id)

  val job5 = Job(
    id = JobId(),
    name = "job5",
    createdOn = now,
    strategy = strategy,
    creator = user1.id)

  // a job may have never completed, for example if the user has forced a new run
  // is this assumption ok? -> yes
  // or do we want to force a completeOn before switching to the new Job? this would be weird
  var run1 = Run((user1.id, job1.id, RunId()), job1.strategy, now)

  var run2 = Run((user1.id, job1.id, RunId()), job1.strategy, now.plusMinutes(5)).completeOn(now.plusMinutes(7))

  var run3 = Run((user1.id, job1.id, RunId()), job1.strategy, now.plusMinutes(10)).completeOn(now.plusMinutes(12))

  var run4 = Run((user1.id, job1.id, RunId()), job1.strategy, now.plusMinutes(15))

  var run5 = Run((user1.id, job5.id, RunId()), job5.strategy, now)

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
      val url = URL("http://example.com/foo/"+i)
      val assertions = for {
        severity <- List(Error, Warning, Info)
        nb = severities(severity)
        j <- 1 to nb
      } yield {
        val assertion = newAssertion(url, assertorId, severity)
        run1 = run1.copy(assertions = run1.assertions + assertion)
        assertion
      }
      val assertorResult = AssertorResult(run1.context, assertorId, url, assertions)
      Run.saveEvent(AssertorResponseEvent(run1.runId, assertorResult)).getOrFail()
    }
    Run.saveEvent(CompleteRunEvent(run2.userId, run2.jobId, run2.runId, run2.completedOn.get)).getOrFail()
    Run.saveEvent(CompleteRunEvent(run3.userId, run3.jobId, run3.runId, run3.completedOn.get)).getOrFail()
  }


  override def beforeAll(): Unit = {
    val start = System.currentTimeMillis
    val initScript = for {
      _ <- conf.db.drop()
      _ <- User.save(user1)
      _ <- User.save(user2)
      _ <- User.save(user3)
      _ <- Job.save(job1)
      _ <- Job.save(job2)
      _ <- Job.save(job3)
      _ <- Job.save(job4)
      _ <- Job.save(job5)
      _ <- Run.saveEvent(CreateRunEvent(run1))
      _ <- Run.saveEvent(CreateRunEvent(run2))
      _ <- Run.saveEvent(CreateRunEvent(run3))
      _ <- Run.saveEvent(CreateRunEvent(run4))
      _ <- Run.saveEvent(CreateRunEvent(run5))
    } yield ()
    initScript.getOrFail()
    addAssertions() // <- already blocking
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

//  "get history of JobDatas for a given jobId" in {
//    // define test logic
//  }

  "get timestamp of latest completed Run for a given job" in {
    val latestCompleted = job1.getCompletedOn().getOrFail(3.seconds)
    latestCompleted must be (run3.completedOn)
  }

  "get timestamp for a job that has never been completed once" in {
    val neverCompleted = job2.getCompletedOn().getOrFail(3.seconds)
    neverCompleted must be(None)
  }


}

class MongoStoreTestLight extends MongoStoreTest(
  nbUrlsPerAssertions = 10,
  severities = Map(Error -> 2, Warning -> 3, Info -> 4),
  nbHttpErrorsPerAssertions = 2,
  nbHttpResponsesPerAssertions = 5,
  nbJobDatas = 3)
