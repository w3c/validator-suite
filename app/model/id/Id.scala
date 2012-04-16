package org.w3.vs.model

import java.util.UUID

class Id(private val uuid: UUID = UUID.randomUUID()) {
  
  def shortId: String = toString.substring(0, 6)

  override def toString = uuid.toString
  
}