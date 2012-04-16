package org.w3.vs.model

import java.util.UUID

case class StrategyId(private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)

object StrategyId {

  def fromString(s: String): StrategyId = StrategyId(UUID.fromString(s))

}