import play.api._
import org.w3.vs.model._
import org.w3.vs.actor._
import org.w3.util._
import org.joda.time.DateTime

object Global extends GlobalSettings {
  
  def vsconf = org.w3.vs.Prod.configuration

  //def store = vsconf.store
  //def OrganizationStore = vsconf.stores.OrganizationStore
  def system = vsconf.system
  implicit def context = vsconf.storeExecutionContext
  
  val logger = play.Logger.of("Global")
  
  override def onStart(app: Application): Unit = {
    
    implicit def configuration = org.w3.vs.Prod.configuration

    val orgId = OrganizationId()
    val tgambet = User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret", organizationId = orgId)
    val bertails = User(email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret", organizationId = orgId)
    val w3c = Organization(id = orgId, name = "World Wide Web Consortium", admin = tgambet)
    
    val w3 = Job(
      createdOn = DateTime.now,
      name = "W3C",
      creatorId = bertails.id,
      organizationId = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/"),
        distance = 2,
        linkCheck = false,
        maxNumberOfResources = 100,
        filter = Filter(include = Everything, exclude = Nothing)))
        
    val tr = Job(
      createdOn = DateTime.now.plus(1000),
      name = "TR",
      creatorId = bertails.id,
      organizationId = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/TR"),
        distance = 2,
        linkCheck = false,
        maxNumberOfResources = 100,
        filter=Filter.includePrefixes("http://www.w3.org/TR")))
        
    val ibm = Job(
      createdOn = DateTime.now.plus(2000),
      name = "IBM",
      creatorId = bertails.id,
      organizationId = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.ibm.com"),
        distance = 2,
        linkCheck = false,
        maxNumberOfResources = 100,
        filter = Filter(include=Everything, exclude=Nothing)))
    
    val lemonde = Job(
      createdOn = DateTime.now.plus(3000),
      name = "Le Monde",
      creatorId = bertails.id,
      organizationId = w3c.id,
      strategy = Strategy(
        entrypoint = URL("http://www.lemonde.fr"),
        distance = 2,
        linkCheck = false,
        maxNumberOfResources = 100,
        filter = Filter(include = Everything, exclude = Nothing)))
    
    // TODO a Saveable trait might be handy
//    tgambet.save()
//    bertails.save()
//    w3c.save()
//    w3.save()
//    tr.save()
//    ibm.save()
//    lemonde.save()

  }
  
  override def onStop(app: Application): Unit = {
    system.shutdown()
  }
  
}
