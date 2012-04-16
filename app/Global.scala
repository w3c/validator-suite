import play.api._
import org.w3.vs.model._
import org.w3.vs.actor._
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
    
    implicit def configuration = org.w3.vs.Prod.configuration
    
    val w3 = Job(
      createdOn = DateTime.now,
      name = "W3C",
      creator = bertails.id,
      organizationId = w3c.id,
      strategy = new Strategy(
        name="irrelevantForV1",
        entrypoint=URL("http://www.w3.org/"),
        distance=2,
        linkCheck=false,
        maxNumberOfResources = 100,
        filter=Filter(include=Everything, exclude=Nothing)))
        
    val tr = Job(
      createdOn = DateTime.now.plus(1000),
      name = "TR",
      creator = bertails.id,
      organizationId = w3c.id,
      strategy = new Strategy(
        name="irrelevantForV1",
        entrypoint=URL("http://www.w3.org/TR"),
        distance=2,
        linkCheck=false,
        maxNumberOfResources = 100,
        filter=Filter.includePrefixes("http://www.w3.org/TR")))
        
    val ibm = Job(
      createdOn = DateTime.now.plus(2000),
      name = "IBM",
      creator = bertails.id,
      organizationId = w3c.id,
      strategy = new Strategy(
        name="irrelevantForV1",
        entrypoint=URL("http://www.ibm.com"),
        distance=2,
        linkCheck=false,
        maxNumberOfResources = 100,
        filter=Filter(include=Everything, exclude=Nothing)))
    
    val lemonde = Job(
      createdOn = DateTime.now.plus(3000),
      name = "Le Monde",
      creator = bertails.id,
      organizationId = w3c.id,
      strategy = new Strategy(
        name="irrelevantForV1",
        entrypoint=URL("http://www.lemonde.fr"),
        distance=2,
        linkCheck=false,
        maxNumberOfResources = 100,
        filter=Filter(include=Everything, exclude=Nothing)))
    
        
    store.putJob(w3)
    store.putJob(tr)
    store.putJob(ibm)
    store.putJob(lemonde)
    
//    var a = List[Job]()
//    for (i <- 0 until 8)
//      a = a :+ job.copy(JobId(), strategy = job.strategy.copy(distance = i), createdOn = DateTime.now.plus(i)) 
//    a.map(store.putJob)

  }
  
  override def onStop(app: Application): Unit = {
    system.shutdown()
  }
  
}




