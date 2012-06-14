package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.vs.actor._
import org.joda.time.DateTime
import scala.math._
import org.w3.banana._
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._

@deprecated("about to be removed", "")
case class JobData (
    id: JobDataId,
    runId: RunId,
    resources: Int,
    errors: Int,
    warnings: Int,
    timestamp: DateTime) {
  
  def health: Int = JobData.health(resources, errors, warnings)

}

object JobData {

  def health(resources: Int, errors: Int, warnings: Int): Int = {
    if (resources == 0) 0
    else {
      val errorAverage = errors.toDouble / resources.toDouble
      val h = (exp(log(0.5) / 10 * errorAverage) * 100).toInt
      scala.math.max(1, h)
    }
  }

  def save(jobData: JobData)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = JobDataBinder.toPointedGraph(jobData).graph
    val result = conf.store.addNamedGraph(JobDataUri(jobData.id), graph)
    FutureVal(result)
  }

  def get(jobDataId: JobDataId)(implicit conf: VSConfiguration): FutureVal[Exception, JobData] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = JobDataUri(jobDataId)
    FutureVal(conf.store.getNamedGraph(uri)) flatMapValidation { graph => 
      val pointed = PointedGraph(uri, graph)
      JobDataBinder.fromPointedGraph(pointed)
    }
  }

  def fromPointedGraph(conf: VSConfiguration)(pointed: PointedGraph[conf.Rdf]): Validation[BananaException, JobData] = {
    implicit val c = conf
    import conf.binders._
    for {
      jobData <- JobDataBinder.fromPointedGraph(pointed)
    } yield {
      jobData
    }
  }

  def fromGraph(conf: VSConfiguration)(graph: conf.Rdf#Graph): Validation[BananaException, Iterable[JobData]] = {
    import conf.diesel._
    import conf.binders._
    val assertions: Iterable[Validation[BananaException, JobData]] =
      graph.getAllInstancesOf(ont.JobData) map { pointed => fromPointedGraph(conf)(pointed) }
    assertions.toList.sequence[({type l[X] = Validation[BananaException, X]})#l, JobData]
  }

  def getForRun(runId: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[JobData]] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders.{ xsd => _, _ }
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?jobDataUri ?p ?o .
} WHERE {
  graph ?contextUri {
    ?jobDataUri a ont:JobData .
    ?jobDataUri ont:runId <#runUri> .
    ?jobDataUri ?p ?o
  }
}
""".replaceAll("#runUri", RunUri(runId).toString)
    val construct = SparqlOps.ConstructQuery(query, ont)
    FutureVal(store.executeConstruct(construct)) flatMapValidation { graph => fromGraph(conf)(graph) }
  }


}
