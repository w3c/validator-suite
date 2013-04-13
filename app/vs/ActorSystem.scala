package org.w3.vs

import akka.util.Timeout
import akka.actor.{DeadLetter, Actor, Props, ActorSystem => AkkaSystem}

trait ActorSystem extends ValidatorSuite {
  this: ValidatorSuite =>

  implicit def timeout: Timeout

  implicit def system: AkkaSystem

  def shutdown(): Unit

}

trait DefaultActorSystem extends ActorSystem {
  this: ValidatorSuite =>

  implicit val timeout: Timeout = {
    import scala.concurrent.duration.Duration
    val r = """^(\d+)([^\d]+)$""".r
    val r(timeoutS, unitS) = config.getString("application.timeout") getOrElse sys.error("application.timeout")
    Timeout(Duration(timeoutS.toInt, unitS))
  }

  implicit val system: AkkaSystem = {
    val vs = AkkaSystem("vs", config.getConfig("application.vs").map(_.underlying) getOrElse sys.error("application.vs"))
    val listener = vs.actorOf(Props(new Actor {
      def receive = {
        case d: DeadLetter => logger.debug("DeadLetter - sender: %s, recipient: %s, message: %s" format(d.sender.toString, d.recipient.toString, d.message.toString))
      }
    }))
    vs.eventStream.subscribe(listener, classOf[DeadLetter])
    vs
  }

  override def shutdown() {
    logger.info(s"Shuting down Akka system: ${system.name}")
    system.shutdown()
    system.awaitTermination()
    super.shutdown()
  }

}