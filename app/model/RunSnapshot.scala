package org.w3.vs.model

import org.w3.util._

import org.joda.time.DateTime

object RunSnapshot {

  def apply(data: RunData): RunSnapshot = {
    import data._
    RunSnapshot(
      jobId = jobId,
      runId = runId,
      explorationMode = explorationMode,
      distance = distance,
      toBeExplored = pending.toList ++ data.toBeExplored,
      fetched = fetched,
      oks = oks,
      errors = errors,
      warnings = warnings)
  }

}

case class RunSnapshot(
    jobId: JobId,
    runId: RunId,
    explorationMode: ExplorationMode,
    distance: Map[URL, Int],
    toBeExplored: List[URL],
    fetched: Set[URL],
    oks: Int,
    errors: Int,
    warnings: Int,
    createdAt: DateTime = new DateTime)
