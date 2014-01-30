package org.w3.vs.store

import org.scalatest.{ Filter => ScalaTestFilter, _ }
import org.scalatest.matchers._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.vs._
import org.w3.vs.web._
import org.w3.vs.util._
import org.w3.vs.util.iteratee._
import org.w3.vs.model._
import org.w3.vs.actor.JobActor
import org.w3.vs.exception._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import scala.concurrent.duration.Duration
import org.w3.vs.util.timer._
import akka.actor.ActorPath
import play.api.libs.iteratee.{ Done => _, Error => _, _ }
import play.api.Mode

abstract class MongoStoreTest(
  nbUrlsPerAssertions: Int,
  severities: Map[AssertionSeverity, Int],
  nbHttpErrorsPerAssertions: Int,
  nbHttpResponsesPerAssertions: Int,
  nbRunDatas: Int)
extends VSTest with WipeoutData {

  implicit val vs = new ValidatorSuite { val mode = Mode.Test }

  val nbAssertionsPerRunPerAssertor = nbUrlsPerAssertions * ( severities(Error) + severities(Warning) + severities(Info) ) /* nb of contexts */
  val nbAssertionsPerRun = 2 /* nb of assertors */ * nbAssertionsPerRunPerAssertor
  val nbAssertionsForJob1 = 2 /* runs */ * nbAssertionsPerRun

  // just for the sake of this test :-)
  val actorName = RunningActorName("foo")

  val user1: User = User.create("foo", "Foo@Example.com", "secret", isRoot = true, credits = 10000)

  val user2 = User.create("bar", "bar@example.com", "secret", isRoot = true, credits = 10000)

  val user3 = User.create("baz", "baz@example.com", "secret", isRoot = true, credits = 10000)
  
  val strategy = Strategy(entrypoint=URL("http://localhost:9001/"), maxResources = 100)

  val strategy2 = Strategy(entrypoint=URL("http://localhost:9001/foo"), maxResources = 100)

  val now = DateTime.now(DateTimeZone.UTC)

  var job1 = Job(
    id = JobId(),
    name = "job1",
    createdOn = now,
    strategy = strategy,
    creatorId = Some(user1.id),
    status = NeverStarted,
    latestDone = None)

  val job2 = Job(
    id = JobId(),
    name = "job2",
    createdOn = now,
    strategy = strategy,
    creatorId = Some(user1.id),
    status = NeverStarted,
    latestDone = None)

  val job3 = Job(
    id = JobId(),
    name = "job3",
    createdOn = now,
    strategy = strategy,
    creatorId = Some(user1.id),
    status = NeverStarted,
    latestDone = None)

  val job4 = Job(
    id = JobId(),
    name = "job4",
    createdOn = now,
    strategy = strategy2,
    creatorId = Some(user2.id),
    status = NeverStarted,
    latestDone = None)

  val run5Id = RunId()

  val job5 = Job(
    id = JobId(),
    name = "job5",
    createdOn = now,
    strategy = strategy,
    creatorId = Some(user1.id),
    status = Running(run5Id, actorName),
    latestDone = None)

  // a job may have never completed, for example if the user has forced a new run
  var run1 = Run(RunId(), job1.strategy)

  var run2 = Run(RunId(), job1.strategy).completeOn(now.plusMinutes(7))

  var run3 = Run(RunId(), job1.strategy).completeOn(now.plusMinutes(12))

  var run4 = Run(RunId(), job1.strategy)

  var run5: Run = Run(run5Id, job5.strategy)

  val assertorIds = List(AssertorId("test_assertor_1"), AssertorId("test_assertor_2"))

  def newAssertion(url: URL, assertor: AssertorId, severity: AssertionSeverity): Assertion = {
    val contexts = Vector(Context("blah", Some(42), None), Context("blarf", None, Some(42)))
    Assertion(
      id = AssertionTypeId(assertor, "some title"),
      url = url,
      assertor = assertor,
      contexts = contexts,
      lang = "fr",
      title = "some title",
      severity = severity,
      description = Some("some description"))
  }

  def createRun1(): Unit = {
    JobActor.saveEvent(CreateRunEvent(Some(user1.id), job1.id, run1.runId, actorName, run1.strategy, now)).getOrFail()
    // add assertions
    var counter = 0
    for {
      assertorId <- assertorIds
      i <- 1 to nbUrlsPerAssertions
    } {
      // Only one AssertorResult for every unique (assertor, url). No partial validations.
      counter += 1
      val url = URL(s"http://example.com/foo/${counter}")
      val assertions = for {
        severity <- List(Error, Warning, Info)
        nb = severities(severity)
        j <- 1 to nb
      } yield {
        val assertion = newAssertion(url, assertorId, severity)
        assertion
      }
      val assertorResult = AssertorResult(assertorId, url, Map(url -> assertions.toVector))
      val are = AssertorResponseEvent(Some(user1.id), job1.id, run1.runId, assertorResult)
      run1 = run1.step(are).run
      JobActor.saveEvent(AssertorResponseEvent(Some(user1.id), job1.id, run1.runId, assertorResult)).getOrFail()
    }
  }

  def createRun2(): Unit = {
    JobActor.saveEvent(CreateRunEvent(Some(user1.id), job1.id, run2.runId, actorName, run2.strategy, now)).getOrFail()
    val doneRunEvent = DoneRunEvent(Some(user1.id), job1.id, run2.runId, Completed, run2.data.resources, run2.data.errors, run2.data.warnings, run2.resourceDatas, run2.groupedAssertionDatas.values, run2.completedOn.get)
    JobActor.saveEvent(doneRunEvent).getOrFail()
  }

  def createRun3(): Unit = {
    JobActor.saveEvent(CreateRunEvent(Some(user1.id), job1.id, run3.runId, actorName, run3.strategy, now)).getOrFail()
    val run3RD = Map(
      URL("http://example.com/foo/1") -> ResourceData(URL("http://example.com/foo/1"), now, 27, 19),
      URL("http://example.com/foo/2") -> ResourceData(URL("http://example.com/foo/2"), now, 27, 19)
    )
    val run3GADs = Map(
      AssertionTypeId("id1") -> GroupedAssertionData(AssertionTypeId("id1"), AssertorId("test_assertor"),"fr", "bar", Warning, 2, Map(URL("http://example.com/foo") -> 1, URL("http://example.com/bar") -> 2)),
      AssertionTypeId("id2") -> GroupedAssertionData(AssertionTypeId("id2"), AssertorId("test_assertor"),"fr", "bar", Warning, 2, Map(URL("http://example.com/foo") -> 1, URL("http://example.com/bar") -> 3))
    )
    run3 = run3.copy(resourceDatas = run3RD, groupedAssertionDatas = run3GADs)
    val doneRunEvent = DoneRunEvent(Some(user1.id), job1.id, run3.runId, Completed, run3.data.resources, run3.data.errors, run3.data.warnings, run3.resourceDatas, run3.groupedAssertionDatas.values, run3.completedOn.get)
    JobActor.saveEvent(doneRunEvent).getOrFail()
  }

  def createRun4(): Unit = {
    JobActor.saveEvent(CreateRunEvent(Some(user1.id), job1.id, run4.runId, actorName, run4.strategy, now)).getOrFail()
    // time to get the latest value for job1 here
    job1 = Job.get(job1.id).getOrFail()
  }

  def createRun5(): Unit = {
    JobActor.saveEvent(CreateRunEvent(Some(user1.id), job5.id, run5.runId, actorName, run5.strategy, now)).getOrFail()
  }

  override def beforeAll(): Unit = {
    //super.beforeAll()
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
    } yield ()
    initScript.getOrFail()
    createRun1()
    createRun2()
    createRun3()
    createRun4()
    createRun5()
    /****/
    val end = System.currentTimeMillis
    val durationInSeconds = (end - start) / 1000.0
    println("DEBUG: it took about " + durationInSeconds + " seconds to load all the entities for this test")
  }

/*  override def afterAll(): Unit = {
    /*connection.close()
    httpClient.close()
    system.shutdown()
    system.awaitTermination()*/
    conf.shutdown()
  }*/

  // User1 got his credits updated after his runs has been saved
  val endUser1 = user1.copy(credits = user1.credits - (strategy.maxResources * 3))

  "User" in {
    val r = User.get(user1.id).getOrFail()
    r should be(endUser1)
  }

  "retrieve User by email" in {
    User.getByEmail("fOO@example.com").getOrFail() should be(endUser1)

    Try { User.getByEmail("unknown@example.com").getOrFail() } should be (Failure(UnknownUser("unknown@example.com")))
  }


  // TODO: Fails with new reactivemongo?
//  "a User can't have an email already in use" in {
//    val user = User.create("FOO", "foo@example.com", "secret", isSubscriber = true)
//    Try { User.save(user).getOrFail() } should be (Failure(DuplicatedEmail("foo@example.com")))
//    Try { User.register("FOO", "foo@example.com", "secret", true).getOrFail() } should be (Failure(DuplicatedEmail("foo@example.com")))
//  }

  "authenticate a user" in {
    Try { User.authenticate("foo@example.com", "secret").getOrFail() } should be (Success(endUser1))

    Try { User.authenticate("foo@example.com", "bouleshit").getOrFail() } should be (Failure(Unauthenticated("foo@example.com")))

    Try {User.authenticate("unknown@example.com", "bouleshit").getOrFail() } should be (Failure(UnknownUser("unknown@example.com")))
  }

  "retrieve unknown Job" in {
    val retrieved = Try { Job.get(JobId()).getOrFail() }
    retrieved should be ('Failure) // TODO test exception type (UnknownJob)
  }

  "create, put, retrieve, delete Job" in {
    val job = job1.copy(id = JobId())
    Try { Job.get(job.id).getOrFail() } should be ('failure)
    Try { Job.save(job).getOrFail() } should be ('success)
    val retrieved = Job.get(job.id).getOrFail(Duration("10s"))
    retrieved should be (job)
    Try { Job.delete(job.id).getOrFail() } should be ('success)
    Try { Job.get(job.id).getOrFail() } should be ('failure)
  }

  "a user can only access the jobs that he created" in {
    val jobs = Job.getFor(user1.id).getOrFail()
    jobs should have size(4)
    jobs should contain (job1)
    jobs should contain (job2)
    jobs should contain (job3)
    jobs should contain (job5)
  }

  "a user with no job should still be able to list his empty list of jobs" in {
    val jobs = Job.getFor(user3.id).getOrFail()
    jobs should be ('empty)
  }

  "retrieve Run" in {
    val run = Run.get(run1.runId).getOrFail(Duration("10s"))._1
    run.assertions.size should be(run1.assertions.size)
  }

  "get all assertions for a completed Run" in {
    val assertions = Run.getAssertions(run1.runId).getOrFail()
    assertions.toList should have length(nbAssertionsPerRun)
  }

  "get all assertions for a completed Run for a given url" in {
    val url = URL("http://example.com/foo/1")
    val assertions = Run.getAssertionsForURL(run1.runId, url).getOrFail()
    assertions.toList should have size(severities(Error) + severities(Warning) + severities(Info))
    assertions.map(_.url).toSet should be(Set(URL("http://example.com/foo/1")))
  }

  "get final ResourceData-s for a given run" in {
    val rds = Run.getResourceDatas(run3.runId).getOrFail()
    rds.toSet should be(run3.resourceDatas.values.toSet)
  }

  "get final ResourceData for a given run and url" in {
    val url = URL("http://example.com/foo/1")
    val rd = Run.getResourceDataForURL(run3.runId, url).getOrFail()
    rd should be(run3.resourceDatas(url))
  }

  "get final GroupedAssertionData-s for a given run" in {
    val gads = Run.getGroupedAssertionDatas(run3.runId).getOrFail()
    gads.toSet should be(run3.groupedAssertionDatas.values.toSet)
  }

  "get all running jobs" in {
    val runningJobs = Job.getRunningJobs().getOrFail()
    runningJobs should have size(2)
    runningJobs should contain(job1)
    runningJobs should contain(job5)
  }

  "Enumerator-s for completed jobs should send the elements from the latest Run" in {
    // first, we terminate job1/run4 and we make it point to run3
    val doneRunEvent = DoneRunEvent(Some(user1.id), job1.id, run3.runId, Completed, run3.data.resources, run3.data.errors, run3.data.warnings, run3.resourceDatas, run3.groupedAssertionDatas.values, run3.completedOn.get)
    JobActor.saveEvent(doneRunEvent).getOrFail()

    // refresh job1
    job1 = Job.get(job1.id).getOrFail()

    // now retrieve the JobData-s from the Enumerator
    val enumJobDatas: List[JobData] =
      (job1.jobDatas() &> Enumeratee.mapConcat(_.toSeq) &> endWithEmpty() |>>> Iteratee.getChunks).getOrFail()
    val Done(_, _, _, runData) = job1.status

    // and compare it to the RunData in the Job
    val jobData = JobData(job1, runData)
    enumJobDatas should be(List(jobData))

    // do the same with RunData
    val enumRunDatas: List[RunData] =
      (job1.runDatas() &> Enumeratee.mapConcat(_.toSeq) &> endWithEmpty() |>>> Iteratee.getChunks).getOrFail()
    enumRunDatas should be(List(runData))

    // ... and resourceDatas
    val enumRds: List[ResourceData] =
      (job1.resourceDatas(forever = false) &> Enumeratee.mapConcat(_.toSeq) &> endWithEmpty() |>>> Iteratee.getChunks).getOrFail()
    val rds = job1.getResourceDatas().getOrFail()
    enumRds should be(rds)

    // ... and GroupedAssertionDatas
    val enumGads: List[GroupedAssertionData] =
      (job1.groupedAssertionDatas() &> Enumeratee.mapConcat(_.toSeq) &> endWithEmpty() |>>> Iteratee.getChunks).getOrFail()
    val gads = job1.getGroupedAssertionDatas().getOrFail()
    enumGads should be(gads)

    // ... and Assertions
    // TODO add assertions in run3: it's currently empty
    val url = URL("http://example.com/nothing-here")
    val enumAssertions: List[Assertion] =
      (job1.assertions(url) &> Enumeratee.mapConcat(_.toSeq) &> endWithEmpty() |>>> Iteratee.getChunks).getOrFail()
    val assertions = job1.getAssertions(url).getOrFail()
    enumAssertions should be(assertions)

  }

  "create a coupon" in {
    val coupon = Coupon(code = "CouponCode", campaign = "campaign", credits = 150)
    intercept[NoSuchCouponException] {
      model.Coupon.get(coupon.code).getOrFail()
    }
    // save it
    coupon.save().getOrFail() should be(coupon)
    // saving it again throws an exception
    intercept[Exception] {
      coupon.save().getOrFail()
    }
    val coupon2 = Coupon(code = "CouponCode", campaign = "campaign2", credits = 1500)
    // can't save a coupon with the same code
    intercept[DuplicateCouponException] {
      coupon2.save().getOrFail()
    }
    val redeemed = coupon.copy(usedBy = Some(UserId()), useDate = Some(DateTime.now(DateTimeZone.UTC)))
    redeemed.update().getOrFail() should be(redeemed)
    Coupon.get(redeemed.code).getOrFail() should be(redeemed)
    val redeemed2 = redeemed.copy(usedBy = Some(UserId()))
    // can't re-redeem a coupon
    intercept[AlreadyUsedCouponException] {
      model.Coupon.redeem(redeemed.code, UserId()).getOrFail()
    }
  }

  /* THIS HAS TO BE AT THE END BECAUSE THERE ARE SIDE-EFFECTS HAPPENING */
  "reInitialize a job" in {
    val jobId = JobId()
    val runId = RunId()
    val job = job1.copy(id = jobId, status = Running(runId, RunningActorName("123456")))
    val run = run1.copy(runId = runId)
    val url = URL("http://example.com/foo")
    val assertion = Assertion(
      id = AssertionTypeId(AssertorId("foo"), "some title"),
      url = url,
      assertor = AssertorId("foo"),
      contexts = Vector.empty,
      lang = "fr",
      title = "some title",
      severity = Warning,
      description = Some("some description"))
    val assertorResult = AssertorResult(AssertorId("foo"), url, Map(url -> Vector(assertion)))
    val script = for {
      _ <- Job.save(job)
      _ <- Run.saveEvent(CreateRunEvent(Some(user1.id), job1.id, run.runId, actorName, run.strategy, now))
      _ <- Run.saveEvent(AssertorResponseEvent(Some(user1.id), job1.id, run.runId, assertorResult))
      assertionsBefore <- Run.getAssertions(run.runId)
      _ <- Job.reset(job.id, removeRunData = true)
      rebornJob <- Job.get(job.id)
      assertionsAfter <- Run.getAssertions(run.runId)
    } yield {
      rebornJob.id should be(job.id)
      rebornJob.status should be(NeverStarted)
      rebornJob.latestDone should be(None)
      assertionsBefore should have size(1)
      assertionsAfter should be('empty)
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
