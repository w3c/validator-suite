package org.w3.vs.actor

import akka.actor._
import org.w3.util.akkaext._
import org.w3.vs.VSConfiguration
import org.w3.vs.model._
import scala.util._
import scalaz.Scalaz._

case class CreateUserAndForward(user: User, tell: Tell)

class UsersActor()(implicit conf: VSConfiguration) extends Actor with PathAwareActor {

  val logger = play.Logger.of(classOf[UsersActor])

  def getUserRefOrCreate(user: User): ActorRef = {
    val id = user.id
    try {
      context.actorOf(Props(new UserActor(user)), name = id.toString)
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / id.toString)
    }
  }

  def receive = {

    case tell @ Tell(Child(id), msg) => {
      //println("passing message to child %s: %s" format (msg.toString, id.toString))
      val from = sender
      val to = self
      context.children.find(_.path.name === id) match {
        case Some(userRef) => userRef forward tell
        case None => {
          import scala.concurrent.ExecutionContext.Implicits.global
          User.get(UserId(id)) onComplete {
            case Success(user) => to.tell(CreateUserAndForward(user, tell), from)
            case Failure(exception) => logger.error("Couldn't find user with id: " + id, exception)
          }
        }
      }
    }

    case CreateUserAndForward(user, tell) => {
      val userRef = getUserRefOrCreate(user)
      userRef forward tell
    }
    
    case a => {logger.error("unexpected message: " + a.toString)}

  }

}
