package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.akkaext._
import org.w3.vs.actor.message._
import org.w3.vs.exception._
import org.w3.vs.VSConfiguration
import akka.actor._
import akka.dispatch._
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._
import org.w3.banana._
import org.w3.banana.diesel._

// closed with its strategy
case class Job(
    id: JobId = JobId(),
    name: String,
    createdOn: DateTime = DateTime.now(DateTimeZone.UTC),
    creatorId: UserId,
    organizationId: OrganizationId,
    strategy: Strategy)(implicit conf: VSConfiguration) {

  import conf.system
  implicit def timeout = conf.timeout
  private val logger = Logger.of(classOf[Job])
  
  def toValueObject: JobVO = 
    JobVO(id, name, createdOn, creatorId, organizationId, strategy.id)
  
  def getCreator: FutureVal[Exception, User] = User.get(creatorId)
  
  def getOrganization: FutureVal[Exception, Organization] = 
    Organization.get(organizationId)
  
  def getHistory: FutureVal[Exception, Iterable[JobData]] = 
    sys.error("@@@")
  
  def getLastCompleted: FutureVal[Exception, Option[DateTime]] = {
    getHistory.map{iterable =>
      val timestamps = iterable.map(_.timestamp)
      if (timestamps.isEmpty)
        None
      else
        Some(timestamps.max)
    }
  }

  def getRun: FutureVal[Throwable, Run] = {
    implicit def ec = conf.webExecutionContext
    (PathAware(organizationsRef, path).?[Run](GetRun))
  }

  def getLastRunAssertions: FutureVal[Exception, Iterable[Assertion]] = {
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(Iterable())
  }
  
  // resource url, time fetched, warnings, errors
  // TODO: optimize by writing the db request directly
  def getURLArticles: FutureVal[Exception, Iterable[(URL, DateTime, Int, Int)]] = {
    Assertion.getForJob(id).map(_.groupBy(_.url).map{case (url, it) => 
      (url, 
       it.map(_.timestamp).max,
       it.count(_.severity == Warning),
       it.count(_.severity == Error)
      )
    })
  }
  
  def getURLArticle(url: URL): FutureVal[Exception, (URL, DateTime, Int, Int)] = {
    getURLArticles.map{it => it.find(_._1 == url)} discard {
      case None => new Exception("Unknown URL") //TODO type exception
    } map {
      case a => a.get
    }
  }
  
  def save(): FutureVal[Exception, Job] = Job.save(this) map { _ => this }
  
  def delete(): FutureVal[Exception, Unit] = {
    cancel()
    Job.delete(id)
  }
  
  def run(): Unit = 
    PathAware(organizationsRef, path) ! Refresh
  
  def cancel(): Unit = 
    PathAware(organizationsRef, path) ! Stop

  def on(): Unit = 
    PathAware(organizationsRef, path) ! BeProactive

  def off(): Unit = 
    PathAware(organizationsRef, path) ! BeLazy
  
  def enumerator: Enumerator[RunUpdate] = {
    logger.error("job enum")
    implicit def ec = conf.webExecutionContext
    val enum = (PathAware(organizationsRef, path).?[Enumerator[RunUpdate]](GetEnumerator))
    Enumerator.flatten(enum failMap (_ => Enumerator.eof[RunUpdate]) toPromise)
  }
  
  private val organizationsRef = system.actorFor(system / "organizations")
  private val path = system / "organizations" / organizationId.toString / "jobs" / id.toString
  def !(message: Any)(implicit sender: ActorRef = null): Unit =
    PathAware(organizationsRef, path) ! message
}

object Job {

  def getJobVO(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, JobVO] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = JobUri(id)
    FutureVal.applyTo(conf.store.getNamedGraph(uri)) flatMap { graph => 
      FutureVal.pureVal[Throwable, JobVO]{
        val pointed = PointedGraph(uri, graph)
        JobVOBinder.fromPointedGraph(pointed)
      }(t => t)
    }
  }


  def get(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Job] = {
    for {
      vo <- getJobVO(id)
      strategy <- Strategy.get(vo.strategyId)
    } yield {
      Job(id, vo.name, vo.createdOn, vo.creatorId, vo.organizationId, strategy)
    }
  }
  
  def getFor(userId: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders.{ xsd => _, _ }
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?jobUri ?p ?o .
  ?s2 ?p2 ?o2
} WHERE {
  graph <#userUri> {
    <#userUri> ont:organizationId ?organizationUri
  } .
  graph ?g {
    ?jobUri ont:organization ?organizationUri .
    ?jobUri ont:strategy ?strategyUri .
    ?jobUri ?p ?o .
  } .
  graph ?strategyUri {
    ?s2 ?p2 ?o2
  }
}
""".replaceAll("#userUri", UserUri(userId).toString)
    val construct = SparqlOps.ConstructQuery(query, ont)
    FutureVal(store.executeConstruct(construct)) flatMapValidation { graph => fromGraph(conf)(graph) }
  }
  
  def getFor(organizationId: OrganizationId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders.{ xsd => _, _ }
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?jobUri ?p ?o .
  ?s2 ?p2 ?o2
} WHERE {
  graph ?g {
    ?jobUri ont:organization <#organizationUri> .
    ?jobUri ont:strategy ?strategyUri .
    ?jobUri ?p ?o .
  } .
  graph ?strategyUri {
    ?s2 ?p2 ?o2
  }
}
""".replaceAll("#organizationUri", OrganizationUri(organizationId).toString)
    val construct = SparqlOps.ConstructQuery(query, ont)
    FutureVal(store.executeConstruct(construct)) flatMapValidation { graph => fromGraph(conf)(graph) }
  }
  
  def getFor(strategyId: StrategyId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders.{ xsd => _, _ }
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?jobUri ?p ?o .
  ?s2 ?p2 ?o2
} WHERE {
  graph ?g {
    ?jobUri ont:strategy <#strategyUri> .
    ?jobUri ?p ?o .
  } .
  graph <#strategyUri> {
    ?s2 ?p2 ?o2
  }
}
""".replaceAll("#strategyUri", StrategyUri(strategyId).toString)
    val construct = SparqlOps.ConstructQuery(query, ont)
    FutureVal(store.executeConstruct(construct)) flatMapValidation { graph => fromGraph(conf)(graph) }
  }
  
  def fromPointedGraph(conf: VSConfiguration)(pointed: PointedGraph[conf.Rdf]): Validation[BananaException, Job] = {
    implicit val c = conf
    import conf.ops._
    import conf.diesel._
    import conf.binders._
    for {
      vo <- JobVOBinder.fromPointedGraph(pointed)
      strategyVO <- (pointed / ont.strategy).exactlyOnePointedGraph.flatMap(StrategyVOBinder.fromPointedGraph(_))
    } yield {
      val strategy = Strategy(strategyVO)
      Job(vo.id, vo.name, vo.createdOn, vo.creatorId, vo.organizationId, strategy)
    }
  }

  def fromGraph(conf: VSConfiguration)(graph: conf.Rdf#Graph): Validation[BananaException, Iterable[Job]] = {
    import conf.diesel._
    import conf.binders._
    val jobsVal: Iterable[Validation[BananaException, Job]] =
      graph.getAllInstancesOf(ont.Job) map { pointed => fromPointedGraph(conf)(pointed) }
    // the Monad for ({type l[X] = Validation[BananaException, X]})#l is provided by banana-rdf
    // there is no instance for Traverse[Iterable] in scalaz, hence the .toList
    jobsVal.toList.sequence[({type l[X] = Validation[BananaException, X]})#l, Job]
  }

  def getCreatedBy(creator: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders.{ xsd => _, _ }
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?jobUri ?p ?o .
  ?s2 ?p2 ?o2
} WHERE {
  graph ?g {
    ?jobUri ont:creator <#creatorUri> .
    ?jobUri ?p ?o .
    ?jobUri ont:strategy ?strategyUri .
  } .
  graph ?strategyUri {
    ?s2 ?p2 ?o2
  }
}
""".replaceAll("#creatorUri", UserUri(creator).toString)
    val construct = SparqlOps.ConstructQuery(query, xsd, ont)
    FutureVal(store.executeConstruct(construct)) flatMapValidation { graph => fromGraph(conf)(graph) }
  }

  
  def saveJobVO(vo: JobVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = JobVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(JobUri(vo.id), graph)
    FutureVal.toFutureValException(FutureVal.applyTo(result))
  }

  def save(job: Job)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    for {
      _ <- saveJobVO(job.toValueObject)
      _ <- Strategy.save(job.strategy)
    } yield ()
  
  def delete(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = sys.error("")

}

