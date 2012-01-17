import play.api._
import org.w3.vs.model._
import akka.actor.ActorSystem
import akka.actor.TypedActor
import org.w3.vs.http.{Http,HttpImpl}
import akka.actor.Props

object Global extends GlobalSettings {

  val logger = play.Logger.of("Global")
  
  override def onStart(app: Application) {
    logger.debug(User.findAll.toString)
    InitialData.insert()
    logger.debug(User.findAll.toString)
  }
  
}

/**
 * Initial set of data to be imported.
 */
object InitialData {
  
  def insert() = {
    
    if(User.findAll.isEmpty) {
      Seq(
        User("tgambet@w3.org", "Thomas Gambet", "secret"),
        User("bertails@w3.org", "Alexandre Bertails", "secret")
      ).foreach(User.create)
    }
    
  }
  
}