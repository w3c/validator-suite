package org.w3.vs.model

import java.util.UUID

case class Action(
    actionId: ObserverId,
    strategy: Strategy,
    state: ActionState)

object Action {
  
  def apply(actionId: ObserverId, strategy: Strategy): Action = {
    Action(ObserverId(), strategy, Crawling)
  }

  
}