package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.akkaext._
import org.w3.vs.actor.message._
import akka.actor._
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import scalaz.Scalaz._
import scalaz._
import org.w3.banana._
import org.w3.banana.util._
import org.w3.banana.LinkedDataStore._
import org.w3.vs._
import diesel._
import org.w3.vs.store.Binders._
import org.w3.vs.sparql._
import org.w3.vs.actor.JobActor._

case class Job(id: JobId, vo: JobVO)(implicit conf: VSConfiguration) {

  import conf._

  val creatorId = vo.creator

  val jobUri = JobUri(vo.organization, id)

  def ldr: LinkedDataResource[Rdf] = LinkedDataResource(jobUri.fragmentLess, vo.toPG)

  val orgUri: Rdf#URI = vo.organization.toUri
  val creatorUri: Rdf#URI = vo.creator.toUri

  private val logger = Logger.of(classOf[Job])
  
  def getCreator(): FutureVal[Exception, User] =
    User.bananaGet(creatorUri).toFutureVal

  def getOrganization(): FutureVal[Exception, Organization] = Organization.get(orgUri)
    
  def getRun(): FutureVal[Exception, Run] = {
    (PathAware(organizationsRef, path) ? GetRun).mapTo[Run]
  }

  def getAssertions(): FutureVal[Exception, Iterable[Assertion]] = {
    getRun() map {
      run => run.assertions.toIterable
    }
  }

  def getActivity(): FutureVal[Exception, RunActivity] = {
    getRun().map(_.activity)
  }

  def getData(): FutureVal[Exception, JobData] = {
    getRun().map(_.jobData)
  }


  // Get all runVos for this job, group by id, and for each runId take the latest completed jobData if any
  def getHistory(): FutureVal[Exception, Iterable[JobData]] = {
    sys.error("")
  }

  def getLastCompleted(): FutureVal[Exception, Option[DateTime]] = {
    Job.getLastCompleted(jobUri)
  }
  
  def save(): FutureVal[Exception, Job] = Job.save(this)
  
  def delete(): FutureVal[Exception, Unit] = {
    cancel()
    Job.delete(this)
  }
  
  def run(): FutureVal[Exception, (OrganizationId, JobId, RunId)] = 
    (PathAware(organizationsRef, path) ? Refresh).mapTo[(OrganizationId, JobId, RunId)]
  
  def cancel(): Unit = 
    PathAware(organizationsRef, path) ! Stop

  def on(): Unit = 
    PathAware(organizationsRef, path) ! BeProactive

  def off(): Unit = 
    PathAware(organizationsRef, path) ! BeLazy

  def resume(): Unit = 
    PathAware(organizationsRef, path) ! Resume

  def getSnapshot(): FutureVal[Exception, JobData] =
    (PathAware(organizationsRef, path) ? GetSnapshot).mapTo[JobData]

  lazy val enumerator: Enumerator[RunUpdate] = {
    val (_enumerator, channel) = Concurrent.broadcast[RunUpdate]
    val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: RunUpdate =>
          try {
            channel.push(msg)
          } catch { 
            case e: ClosedChannelException => {
              logger.error("ClosedChannel exception: ", e)
              channel.eofAndEnd()
            }
            case e => {
              logger.error("Enumerator exception: ", e)
              channel.eofAndEnd()
            }
          }
        case msg => logger.error("subscriber got " + msg)
      }
    }))
    listen(subscriber)
    _enumerator
  }

  def listen(implicit listener: ActorRef): Unit =
    PathAware(organizationsRef, path).tell(Listen(listener), listener)
  
  def deafen(implicit listener: ActorRef): Unit =
    PathAware(organizationsRef, path).tell(Deafen(listener), listener)
  
  private val organizationsRef = system.actorFor(system / "organizations")

  private val path = {
    /* val relPath = jobUri.relativize(URI("https://validator.w3.org/suite/")).getString */
    system / "organizations" / vo.organization.id / "jobs" / id.id
  }
  
  def !(message: Any)(implicit sender: ActorRef = null): Unit =
    PathAware(organizationsRef, path) ! message

}

object Job {

  def apply(
    id: JobId = JobId(),
    name: String,
    createdOn: DateTime = DateTime.now(DateTimeZone.UTC),
    strategy: Strategy,
    creator: UserId,
    organization: OrganizationId)(
    implicit conf: VSConfiguration): Job =
      Job(id, JobVO(name, createdOn, strategy, creator, organization))

  implicit def toVO(job: Job): JobVO = job.vo

  def get(orgId: OrganizationId, jobId: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, (Job, Option[Rdf#URI])] =
    get(JobUri(orgId, jobId))

  def bananaGet(orgId: OrganizationId, jobId: JobId)(implicit conf: VSConfiguration): BananaFuture[(Job, Option[Rdf#URI])] =
    bananaGet((orgId, jobId).toUri)

  def bananaGet(jobUri: Rdf#URI)(implicit conf: VSConfiguration): BananaFuture[(Job, Option[Rdf#URI])] = {
    import conf._
    for {
      ids <- JobUri.fromUri(jobUri).bf
      jobLDR <- store.GET(jobUri)
      runUriOpt <- (jobLDR.resource / ont.run).asOption[Rdf#URI]
      jobVO <- jobLDR.resource.as[JobVO]
    } yield (Job(ids._2, jobVO), runUriOpt)
  }

  def get(jobUri: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, (Job, Option[Rdf#URI])] = {
    import conf._
    bananaGet(jobUri).toFutureVal
  }

  def getFor(userId: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
    import conf._
    val query = """
CONSTRUCT {
  ?user ont:organization ?org .
  ?org ont:job ?job .
  ?s2 ?p2 ?o2
} WHERE {
  BIND (iri(strbefore(str(?user), "#")) AS ?userG) .
  graph ?userG {
    ?user ont:organization ?org .
  } .
  BIND (iri(strbefore(str(?org), "#")) AS ?orgG) .
  graph ?orgG {
    ?org ont:job ?job .
  } .
  BIND (iri(strbefore(str(?job), "#")) AS ?jobG) .
  graph ?jobG {
    ?s2 ?p2 ?o2
  }
}
"""
    val construct = ConstructQuery(query, ont)
    val r = for {
      graph <- store.executeConstruct(construct, Map("user" -> userId.toUri))
      pointedOrg = PointedGraph[Rdf](userId.toUri, graph)
      it <- (pointedOrg / ont.organization / ont.job).asSet2[(OrganizationId, JobId), JobVO]
    } yield {
      it map { case (ids, jobVO) => Job(ids._2, jobVO) }
    }
    r.toFutureVal
  }

  
  def getFor(orgUri: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
    import conf._
    val query = """
CONSTRUCT {
  ?org ont:job ?job .
  ?s2 ?p2 ?o2
} WHERE {
  BIND (iri(strbefore(str(?org), "#")) AS ?orgG) .
  graph ?orgG {
    ?org ont:job ?job .
  } .
  BIND (iri(strbefore(str(?job), "#")) AS ?jobG) .
  graph ?jobG {
    ?s2 ?p2 ?o2
  }
}
"""
    val construct = ConstructQuery(query, ont)
    val r = for {
      graph <- store.executeConstruct(construct, Map("org" -> orgUri))
      pointedOrg = PointedGraph[Rdf](orgUri, graph)
      it <- (pointedOrg / ont.job).asSet2[(OrganizationId, JobId), JobVO]
    } yield {
      it map { case (ids, jobVO) => Job(ids._2, jobVO) }
    }
    r.toFutureVal
  }

  def getFor(organizationId: OrganizationId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] =
    getFor(organizationId.toUri)
  
  def getCreatedBy(creator: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
    sys.error("to be implemented?")
  }

  def getLast(jobUri: Rdf#URI, property: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, Option[(RunId, DateTime)]] = {
    import conf._
    val query = """
SELECT ?run ?timestamp WHERE {
  BIND (iri(strbefore(str(?job), "#")) AS ?jobG) .
  graph ?jobG {
    ?job ont:run ?run
  }
  BIND (iri(strbefore(str(?run), "#")) AS ?runG) .
  graph ?runG {
    ?run ?prop ?timestamp
  }
}
"""
    val select = SelectQuery(query, ont)
    val r = store.executeSelect(select, Map("job" -> jobUri, "prop" -> property)) map { rows =>
      val rds: Iterable[BananaValidation[(RunId, DateTime)]] = rows.toIterable map { row =>
        val runId = row("run").flatMap(_.as[(OrganizationId, JobId, RunId)]).map(_._3)
        val timestamp = row("timestamp").flatMap(_.as[DateTime])
        ^[BananaValidation, RunId, DateTime, (RunId, DateTime)](runId, timestamp)(Tuple2.apply)
      }
      rds.toList.sequence match {
        case Failure(_) => None
        case Success(it) if it.isEmpty => None
        case Success(it) => Some(it.maxBy(_._2))
      }
    }
    r.toFutureVal
  }

  def getLastCreated(jobUri: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, Option[(RunId, DateTime)]] = {
    import conf._
    getLast(jobUri, ont.createdAt.uri)
  }

  def getLastCompleted(jobUri: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, Option[DateTime]] = {
    import conf._
    getLast(jobUri, ont.completedAt.uri) map { _.map { _._2 } }
  }

  def save(job: Job)(implicit conf: VSConfiguration): FutureVal[Exception, Job] = {
    import conf._
    val orgUri = job.vo.organization.toUri
    val creatorUri = job.vo.creator.toUri
    // TODO make one command
    val r = for {
      _ <- store.PUT(job.ldr)
      _ <- store.POST(orgUri, orgUri -- ont.job ->- job.jobUri)
      _ <- store.POST(creatorUri, creatorUri -- ont.job ->- job.jobUri)
    } yield job
    r.toFutureVal
  }

  def delete(job: Job)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf._
    import ops._
    // TODO make one command
    val r = for {
      _ <- store.PATCH(job.orgUri,
                       delete = List((job.orgUri, ont.job.uri, job.jobUri))) // <- bug here
      _ <- store.DELETE(job.jobUri.fragmentLess)
    } yield ()
    r.toFutureVal
  }

}

