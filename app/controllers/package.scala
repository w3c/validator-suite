package org.w3.vs

import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import org.w3.vs.model._
import play.api.mvc.PathBindable

package object controllers {

  implicit val bindableJobId = new PathBindable[JobId] {
    def bind (key: String, value: String): Either[String, JobId] = {
      try {
        Right(JobId(value))
      } catch { case e: Exception =>
        Left("invalid id: " + value)
      }
    }
    def unbind (key: String, value: JobId): String = {
      value.toString
    }
  }

  implicit val assertionJobId = new PathBindable[AssertionId] {
    def bind (key: String, value: String): Either[String, AssertionId] = {
      try {
        Right(AssertionId(value))
      } catch { case e: Exception =>
        Left("invalid id: " + value)
      }
    }
    def unbind (key: String, value: AssertionId): String = {
      value.toString
    }
  }

  implicit val bindableURL = new PathBindable[URL] {
    def bind (key: String, value: String): Either[String, URL] = {
      try {
        Right(new URL(URLDecoder.decode(value, "UTF-8")))
      } catch { case e: Exception =>
        Left("invalid url: " + value)
      }
    }
    def unbind (key: String, value: URL): String = {
      URLEncoder.encode(value.toString, "UTF-8")
    }
  }
  
}
