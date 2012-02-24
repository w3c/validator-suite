package org.w3.vs.util

import play.api.mvc.PathBindable

object Binders {
  
  implicit val uuidPathBindable = new PathBindable[java.util.UUID] {
    def bind (key: String, value: String): Either[String, java.util.UUID] = {
      try {
        Right(java.util.UUID.fromString(value))
      } catch { case e: Exception =>
        Left("invalid id: " + value)
      }
    }
    def unbind (key: String, value: java.util.UUID): String = {
      value.toString
    }
  }
}