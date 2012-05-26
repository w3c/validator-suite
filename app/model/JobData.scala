package org.w3.vs.model

import org.w3.util._
import org.w3.vs.actor._
import org.joda.time.DateTime

case class JobData (
    id: JobDataId = JobDataId(),
    jobId: JobId,
    runId: RunId,
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0,
    timestamp: DateTime = DateTime.now) {
  
  def toValueObject: JobDataVO = JobDataVO(id, jobId, runId, resources, errors, warnings, timestamp)
}

object JobData {
  def get(id: JobDataId): FutureVal[Exception, JobData] = sys.error("")
  def getForJob(id: JobId): FutureVal[Exception, Iterable[JobData]] = sys.error("")
  def getForRun(id: RunId): FutureVal[Exception, Iterable[JobData]] = sys.error("")
}