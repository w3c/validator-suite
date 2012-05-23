package org.w3.vs.model

import org.w3.util._
import org.w3.vs.actor._
import org.joda.time.DateTime

object JobData {
  
  def apply(
      id: JobDataId = JobDataId(),
      jobId: JobId,
      runId: RunId,
      resources: Int = 0,
      errors: Int = 0,
      warnings: Int = 0,
      timestamp: DateTime = DateTime.now): JobData = {
    JobData(JobDataVO(id, jobId, runId, resources, errors, warnings, timestamp))
  }
  
  def get(id: JobDataId): FutureVal[Exception, JobData] = sys.error("")
  def getForJob(id: JobId): FutureVal[Exception, Iterable[JobData]] = sys.error("")
  def getForRun(id: RunId): FutureVal[Exception, Iterable[JobData]] = sys.error("")
  
}

case class JobDataVO(
    id: JobDataId = JobDataId(),
    jobId: JobId,
    runId: RunId,
    resources: Int,
    errors: Int,
    warnings: Int,
    timestamp: DateTime = DateTime.now)

case class JobData(valueObject: JobDataVO) {
  
  def id: JobDataId = valueObject.id
  def resources: Int = valueObject.resources
  def errors: Int = valueObject.errors
  def warnings: Int = valueObject.warnings
  def timestamp: DateTime = valueObject.timestamp
  
  def withData(errors: Int = errors, warnings: Int = warnings, resources: Int = resources): JobData = {
    copy(valueObject.copy(errors = errors, warnings = warnings, resources = resources))
  }
  
}