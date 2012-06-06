package org.w3.vs.model

import org.scalatest._
import org.scalatest.matchers._
import scalaz._
import org.w3.banana._
import org.w3.banana.jena._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.URL
import org.w3.vs._

class StoreTest extends WordSpec with MustMatchers with BeforeAndAfterAll {

  implicit val conf: VSConfiguration = new DefaultProdConfiguration { }

  // def testSerializeDeserialize[T](binder: PointedGraphBinder[Jena, T])(t: T) = {
  //   import binder._
  //   fromPointedGraph(toPointedGraph(t)) must be === (Success(t))
  // }

  import akka.util.duration._

  val org = Organization(id = OrganizationId(), name = "World Wide Web Consortium", adminId = UserId())

  val user = User(UserId(), "foo", "foo@example.com", "secret", org.id)
  
  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
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

  override def beforeAll(): Unit = {
    Organization.save(org)
    User.save(user)    
    Job.save(job1)
    Job.save(job2)
    Job.save(job3)
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
    val jobs = Job.getCreatedBy(user).result(1.second) getOrElse sys.error("")
    jobs must have size(3)
    jobs must contain (job1)
    jobs must contain (job2)
    jobs must contain (job3)
  }

//  "OrganizationVO" in {
//    testSerializeDeserialize(OrganizationVOBinder) {
//      OrganizationVO(name = "foo", admin = UserId())
//    }
//  }
//
//  "JobVO" in {
//    testSerializeDeserialize(JobVOBinder) {
//      JobVO(
//        name = "foo",
//        createdOn = DateTime.now(DateTimeZone.UTC),
//        creatorId = UserId(),
//        organizationId = OrganizationId(),
//        strategyId = StrategyId())
//    }
//  }
//
//  "AssertionVO no description" in {
//    testSerializeDeserialize(AssertionVOBinder) {
//      AssertionVO(
//        jobId = JobId(),
//        runId = RunId(),
//        assertorId = AssertorId(),
//        url = URL("http://example.com/foo"),
//        lang = "fr",
//        title = "bar",
//        severity = Warning,
//        description = None,
//        timestamp = DateTime.now(DateTimeZone.UTC))
//    }
//  }
//
//  "AssertionVO with description" in {
//    testSerializeDeserialize(AssertionVOBinder) {
//      AssertionVO(
//        jobId = JobId(),
//        runId = RunId(),
//        assertorId = AssertorId(),
//        url = URL("http://example.com/foo"),
//        lang = "fr",
//        title = "bar",
//        severity = Warning,
//        description = Some("some desc"),
//        timestamp = DateTime.now(DateTimeZone.UTC))
//    }
//  }
//
//  "ContextVO" in {
//    testSerializeDeserialize(ContextVOBinder) {
//      ContextVO(
//        content = "foo",
//        line = Some(42),
//        column = None,
//        assertionId = AssertionId())
//    }
//  }
//
//  /*"AssertorResultVO" in {
//    testSerializeDeserialize(AssertorResultVOBinder) {
//      AssertorResultVO(
//        jobId = JobId(),
//        runId = RunId(),
//        assertorId = AssertorId(),
//        sourceUrl = URL("http://example.com/foo"),
//        timestamp = DateTime.now(DateTimeZone.UTC))
//
//    }
//  }*/
//
//  "JobDataVO" in {
//    testSerializeDeserialize(JobDataVOBinder) {
//      JobDataVO(
//        jobId = JobId(),
//        resources = 42,
//        errors = 43,
//        warnings = 44,
//        timestamp = DateTime.now(DateTimeZone.UTC))
//    }
//  }
//
//  val errorResponseVO =
//    ErrorResponseVO(
//      jobId = JobId(),
//      runId = RunId(),
//      url = URL("http://example.com/foo"),
//      action = GET,
//      timestamp = DateTime.now(DateTimeZone.UTC),
//      why = "just because")
//
//  "ErrorResponseVO" in {
//    testSerializeDeserialize(ErrorResponseVOBinder)(errorResponseVO)
//  }
//
//  val httpResponseVO =
//    HttpResponseVO(
//      jobId = JobId(),
//      runId = RunId(),
//      url = URL("http://example.com/foo"),
//      action = GET,
//      timestamp = DateTime.now(DateTimeZone.UTC),
//      status = 200,
//      headers = Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz")),
//      extractedURLs = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")))
//
//  "HttpResponseVO" in {
//    testSerializeDeserialize(HttpResponseVOBinder)(httpResponseVO)
//  }
//
//  "ResourceResponseVO as ErrorResponseVO" in {
//    testSerializeDeserialize(ResourceResponseVOBinder)(errorResponseVO)
//  }
//
//  "ResourceResponseVO as HttpResponseVO" in {
//    testSerializeDeserialize(ResourceResponseVOBinder)(httpResponseVO)
//  }
//
//  "RunVO" in {
//    testSerializeDeserialize(RunVOBinder) {
//      RunVO(
//        toBeExplored = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")),
//        fetched = Set(URL("http://example.com/foo"), URL("http://example.com/bar")),
//        createdAt = DateTime.now(DateTimeZone.UTC),
//        jobId = JobId(),
//        jobDataId = JobDataId())
//    }
//  }
//
//  "StrategyVO" in {
//    testSerializeDeserialize(StrategyVOBinder) {
//      StrategyVO(
//        entrypoint = URL("http://example.com/foo"),
//        distance = 2,
//        linkCheck = true,
//        maxResources = 100,
//        filter = Filter.includeEverything)
//    }
//  }
//
//
//  "UserVO" in {
//    testSerializeDeserialize(UserVOBinder) {
//      UserVO(
//        name = "foo bar",
//        email = "foo@example.com",
//        password = "secret",
//        organizationId = OrganizationId())
//    }
//  }


}
