import play.api._
import org.w3.vs.model._
import org.w3.util._

object Global extends GlobalSettings {

  val logger = play.Logger.of("Global")
  
  override def onStart(app: Application): Unit = {
    import org.w3.vs.prod.configuration.store
    val jobW3C = new Job(name="W3C", 
        strategy = new EntryPointStrategy(
          name="irrelevantForV1",
          entrypoint=URL("http://www.w3.org/"),
          distance=0,
          linkCheck=false,
          filter=Filter(include=Everything, exclude=Nothing)))
    store.putJob(jobW3C)
    store.saveUser(User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret").withJob(jobW3C))
    store.saveUser(User(email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret").withJob(jobW3C))
  }
  
  
}
