import play.api._

import org.w3.vs.model._

object Global extends GlobalSettings {
  
  override def onStart(app: Application) {
    InitialData.insert()
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