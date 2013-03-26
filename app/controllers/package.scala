package org.w3.vs

import java.net.URLDecoder
import java.net.URLEncoder
import org.w3.vs.model._
import play.api.mvc.{AnyContent, Action, QueryStringBindable, PathBindable}
import play.api.mvc.QueryStringBindable.Parsing

package object controllers {

  type URL = org.w3.util.URL
  type SocketType = SocketType.SocketType
  type ActionA = Action[AnyContent]

  val socketEnum = new Enumeration() {}

  object SocketType extends Enumeration {
    type SocketType = Value
    val ws, events, comet = Value
  }

  implicit val bindableSocketType = new PathBindable[SocketType] {
    def bind(key: String, value: String): Either[String, SocketType] = {
      try {
        Right(SocketType.withName(value))
      } catch { case e: Exception =>
        Left("invalid socket type: " + value)
      }
    }
    def unbind(key: String, value: SocketType): String = {
      value.toString
    }
  }

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

//  implicit val assertionJobId = new PathBindable[AssertionId] {
//    def bind (key: String, value: String): Either[String, AssertionId] = {
//      try {
//        Right(AssertionId(value))
//      } catch { case e: Exception =>
//        Left("invalid id: " + value)
//      }
//    }
//    def unbind (key: String, value: AssertionId): String = {
//      value.toString
//    }
//  }

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

  implicit object urlQueryStringBinder extends Parsing[URL] (
    s => new URL(URLDecoder.decode(s, "UTF-8")),
    url => URLEncoder.encode(url.toString, "UTF-8"),
    (key: String, e: Exception) => "Cannot parse parameter %s as URL: %s".format(key, e.getMessage)
  )
  
}
