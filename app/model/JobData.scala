package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.vs.actor._
import org.joda.time.DateTime
import scala.math._
import org.w3.banana._
import scalaz.Scalaz._

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

}
