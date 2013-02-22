package org.w3.vs

import akka.actor.{ ActorRef, ActorSystem }
import scala.concurrent._
import com.ning.http.client.AsyncHttpClient
import org.w3.vs.http.Cache
import akka.util.Timeout
import reactivemongo.api.{ DefaultDB, MongoConnection }
import com.mongodb.{ MongoClient, DB }
import org.w3.vs.actor.RunEventBus

trait VSConfiguration {
  
  implicit val system: ActorSystem

  val runEventBus: RunEventBus
  
  val runsActorRef: ActorRef

  val httpActorRef: ActorRef

  implicit val timeout: Timeout

  val httpCacheOpt: Option[Cache]

  val httpClient: AsyncHttpClient

  val connection: MongoConnection

  val db: DefaultDB

  // these are only temporary because of a bug in Play

  val mongoClient: MongoClient

  val mongoDb: DB
}
