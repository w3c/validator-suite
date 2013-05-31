package org.w3.vs.model

import org.w3.vs.ValidatorSuite
import akka.actor.ActorPath

case class RunningActorName(name: String) {

  override def toString: String = name

  def actorPath(implicit conf: ValidatorSuite): ActorPath = conf.runsActorRef.path / name

}
