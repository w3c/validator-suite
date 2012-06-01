package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.vs.actor._
import org.joda.time.DateTime
import scala.math._

case class JobData (
    id: JobDataId = JobDataId(),
    jobId: JobId,
    //runId: RunId = RunId(), // i'm not sure a jobData should contain a runId, a runId already has a reference to its jobData. Also it's not convenient to have to have a runId to create a JobData object
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0,
    timestamp: DateTime = DateTime.now)(implicit conf: VSConfiguration) {
  
  def toValueObject: JobDataVO = JobDataVO(id, jobId, resources, errors, warnings, timestamp)
  
  def health(): Int = {
    if (resources == 0) 0
    else {
      val errorAverage = errors.toDouble / resources.toDouble
      (exp(log(0.5) / 10 * errorAverage) * 100).toInt
    }
  }
}

object JobData {
  def get(id: JobDataId)(implicit conf: VSConfiguration): FutureVal[Exception, JobData] = sys.error("")
  def getForJob(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[JobData]] = sys.error("")
  def getForRun(id: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[JobData]] = sys.error("")
}