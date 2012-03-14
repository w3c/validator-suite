package org.w3.vs.run

import org.w3.vs.model._
import org.w3.util._

import org.joda.time.DateTime

case class RunSnapshot(
    jobId: Job#Id,
    distance: Map[URL, Int],
    toBeExplored: List[URL],
    fetched: Set[URL],
    fsmState: FSMState,
    oks: Int,
    errors: Int,
    warnings: Int,
    createdAt: DateTime = new DateTime)
