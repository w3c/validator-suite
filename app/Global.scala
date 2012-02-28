import play.api._
import org.w3.vs.model._

object Global extends GlobalSettings {

  val logger = play.Logger.of("Global")
  
  override def onStart(app: Application): Unit = {
    import org.w3.vs.prod.configuration.store
    store.saveUser(User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret"))
    store.saveUser(User(email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret"))
  }
  
  
}
