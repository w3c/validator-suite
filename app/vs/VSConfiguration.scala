package org.w3.vs

import akka.actor.ActorSystem
import scala.concurrent._
import com.ning.http.client.AsyncHttpClient
import org.w3.vs.http.Cache
import akka.util.Timeout
import reactivemongo.api.{ DefaultDB, MongoConnection }

trait VSConfiguration {
  
  implicit val system: ActorSystem
  
  implicit val timeout: Timeout

  val httpCacheOpt: Option[Cache]

  val httpClient: AsyncHttpClient

  val connection: MongoConnection

  val db: DefaultDB

}
