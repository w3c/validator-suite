import play.api._
import org.w3.vs.model._
import org.w3.util._
import org.joda.time.DateTime

object Global extends GlobalSettings {
  
  def vsconf = org.w3.vs.Prod.configuration

  def store = vsconf.store
  def system = vsconf.system
  
  val logger = play.Logger.of("Global")
  
  override def onStart(app: Application): Unit = {
    
    val w3c = OrganizationData(name="World Wide Web Consortium")

    store.putOrganization(w3c)
    
    val tgambet = User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret", organization = w3c.id)
    val bertails = User(email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret", organization = w3c.id)

    store.saveUser(User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret", organization = w3c.id))
    store.saveUser(User(email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret", organization = w3c.id))
    
    val job = JobConfiguration(
      name = "W3C",
      creator = bertails.id,
      organization = w3c.id,
      strategy = new Strategy(
        name="irrelevantForV1",
        entrypoint=URL("http://www.w3.org/"),
        distance=1,
        linkCheck=false,
        maxNumberOfResources = 100,
        filter=Filter(include=Everything, exclude=Nothing)))
    
    var a = List[JobConfiguration]()
    for (i <- 0 until 8)
      a = a :+ job.copy(JobId(), strategy = job.strategy.copy(distance = i), createdOn = DateTime.now.plus(i)) 
    a.map(store.putJob)

  }
  
  override def onStop(app: Application): Unit = {
    system.shutdown()
  }
  
}




