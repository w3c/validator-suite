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
import org.w3.vs.DefaultProdConfiguration

abstract class StoreTest(
  nbUrlsPerAssertions: Int,
  nbErrors: Int,
  nbWarnings: Int,
  nbInfos: Int,
  nbHttpErrorsPerAssertions: Int,
  nbHttpResponsesPerAssertions: Int,
  nbJobDatas: Int)
extends WordSpec with MustMatchers with BeforeAndAfterAll with Inside {

  val nbAssertionsPerRunPerAssertor = nbUrlsPerAssertions * ( nbErrors + nbWarnings + nbInfos ) /* nb of contexts */
  val nbAssertionsPerRun = 2 /* nb of assertors */ * nbAssertionsPerRunPerAssertor
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
      filter=Filter(include=Everything, exclude=Nothing))

  val strategy2 =
    Strategy( 
      entrypoint=URL("http://localhost:9001/foo"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing))
  
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

  val now = DateTime.now(DateTimeZone.UTC)

  // a job may have never completed, for example if the user has forced a new run
  // is this assumption ok? -> yes
  // or do we want to force a completedAt before switching to the new Job? this would be weird
  val run1 = Run(job = job1, createdAt = now, completedAt = None)

  val run2 = Run(job = job1, createdAt = now.plusMinutes(5), completedAt = Some(now.plusMinutes(7)))

  val run3 = Run(job = job1, createdAt = now.plusMinutes(10), completedAt = Some(now.plusMinutes(12)))

  val run4 = Run(job = job1, createdAt = now.plusMinutes(15), completedAt = None)

  val assertorIds = List(AssertorId(), AssertorId())

  // assertions for job1
  val assertions: Vector[Assertion] = {
    def newAssertion(runId: RunId, assertorId: AssertorId, url: URL, severity: AssertionSeverity): Assertion =
      Assertion(
        jobId = job1.id,
        runId = runId,
        assertorId = assertorId,
        url = url,
        lang = "fr",
        title = "some title",
        severity = severity,
        description = Some("some description"))
    ///
    // val a = newAssertion(run1.id, assertorIds(0), URL("http://example.com/foo/1"), Error)
    // import conf.binders._
    // val pointed = AssertionVOBinder.toPointedGraph(a.toValueObject)
    // Jena.dump(pointed.graph)
    ///
    val builder = Vector.newBuilder[Assertion]
    builder.sizeHint(nbAssertionsForJob1)
    for ( runId <- List(run1.id, run2.id) ; assertorId <- assertorIds ; i <- 1 to nbUrlsPerAssertions ) {
      for ( j <- 1 to nbErrors ) builder += newAssertion(runId, assertorId, URL("http://example.com/foo/"+i), Error)
      for ( j <- 1 to nbWarnings ) builder += newAssertion(runId, assertorId, URL("http://example.com/foo/"+i), Warning)
      for ( j <- 1 to nbInfos ) builder += newAssertion(runId, assertorId, URL("http://example.com/foo/"+i), Info)
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

  implicit val context = conf.webExecutionContext
  
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
      _ <- Run.save(run3)
      _ <- Run.save(run4)
      _ <- FutureVal.sequence(assertions map { Assertion.save _ })
      _ <- FutureVal.sequence(contexts map { Context.save _ })
      _ <- FutureVal.sequence(resourceResponses map { ResourceResponse.save _ }) 
    } yield ()).await(30.second)
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
  
  "create, put, retrieve, delete Job" in {
    val job = job1.copy(id = JobId())
    Job.get(job.id).result(1.second) must be ('failure)
    Job.save(job).result(1.second) must be ('success)
    val retrieved = Job.get(job.id).result(10.second)
    retrieved must be (Success(job))
    Job.delete(job).result(1.second) must be ('success)
    Job.get(job.id).result(1.second) must be ('failure)
  }

  "retrieve Organization" in {
    val retrieved = Organization.get(org.id).result(1.second)
    retrieved must be === (Success(org))
  }

  "save and retrieve User" in {
    val retrieved = User.get(user.id).result(1.second)
    retrieved must be === (Success(user))
  }

  "retrieve User by email" in {
    User.getByEmail("foo@example.com").result(2.second) must be === (Success(user))

    User.getByEmail("unknown@example.com").result(2.second) must be === (Failure(UnknownUser))
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

  "get all RunVOs given a JobId" in {
    val retrieved = Run.getRunVOs(job1.id).result(2.second) getOrElse sys.error("test Run.getRunVOs")
    retrieved must have size (4)
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

  "get all URLArticles from a run" in {
    val retrieved = run1.getURLArticles().result(2.seconds) getOrElse sys.error("test Run.getURLArticles")
    retrieved must have size (nbUrlsPerAssertions)
    retrieved foreach { case (url, latest, warnings, errors) =>
      warnings must be (assertorIds.size * 2 /* ctx */ * nbWarnings)
      errors must be (assertorIds.size * 2 /* ctx */ * nbErrors)
    }
  }
  
  "get the URLArticle for a specific URL" in {
    val url = URL("http://www.test.com")
    Assertion(
      jobId = job1,
      runId = run1,
      assertorId = assertorIds(0),
      url = url,
      lang = "fr",
      title = "some title",
      severity = Warning,
      description = Some("some description")).save().await(10.seconds)
    // I'm not sure why run1.getURLArticle(url) returns nothing here. What am i doing wrong?
    val retrieved = run1.getURLArticle(url).result(1.second).fold(f => throw f, s => s)
    retrieved._3 must be (1)
    retrieved._4 must be (0) // I think that with the current implementation this will fail and return 1 instead of 0 
  }

  "get all URLArticles from a run with no assertions" in {
    val retrieved = run3.getURLArticles().result(2.seconds) getOrElse sys.error("test Run.getURLArticles")
    retrieved must be ('empty)
  }

  "getAssertorArticles must return the assertors that validated @url, with their name and the total number of warnings and errors that they reported for @url." in {
    val retrieved = run1.getAssertorArticles(URL("http://example.com/foo/1")).result(1.seconds) getOrElse sys.error("test Run.getAssertorArticles")
    retrieved must have size (2)
    retrieved foreach { resultPerAssertor =>
      inside(resultPerAssertor) {
        case (assertorId, _, foundWarnings, foundErrors) => {
          foundWarnings must be (nbWarnings*2)
          foundErrors must be (nbErrors*2)
        }
      }
    }
  }

  "retrieve ResourceResponse" in {
    val rr = resourceResponses(0)
    val retrieved = ResourceResponse.get(rr.runId, rr.id).result(1.second)
    retrieved must be (Success(rr))
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

  "get history of JobDatas for a given jobId" in {
    // define test logic
  }

  "get timestamp of latest completed Run for given jobId" in {
    val retrieved = job1.getLastCompleted().result(1.second) getOrElse sys.error("test Job.getLastCompleted")
    retrieved must be === (run3.completedAt)
  }

  "get timestamp of latest completed Run for given jobId that has never been completed once" in {
    val retrieved = job2.getLastCompleted().result(1.second) getOrElse sys.error("test Job.getLastCompleted")
    retrieved must be === (None)
  }

}


class StoreTestLight extends StoreTest(
  nbUrlsPerAssertions = 10,
  nbErrors = 2,
  nbWarnings = 3,
  nbInfos = 4,
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
