package play.api

//import play.api._
import org.w3.vs.model._
import org.w3.vs.actor._
import org.w3.util._
import org.joda.time.{ DateTime, DateTimeZone }

object Global extends GlobalSettings {
  
  def vsconf = org.w3.vs.Prod.configuration

  //def store = vsconf.store
  //def OrganizationStore = vsconf.stores.OrganizationStore
  def system = vsconf.system
  
  val logger = play.Logger.of("Global")
  
  implicit def conf = org.w3.vs.Prod.configuration
  
  val orgId = OrganizationId()
  
  val tgambet = User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret", organizationId = orgId)
  val bertails = User(email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret", organizationId = orgId)
  val w3c = Organization(id = orgId, name = "World Wide Web Consortium", adminId = tgambet.id)
    
  val w3 = Job(
    createdOn = DateTime.now(DateTimeZone.UTC),
    name = "W3C",
    creatorId = bertails.id,
    organizationId = w3c.id,
    strategy = Strategy(
      entrypoint = URL("http://www.w3.org/"),
      linkCheck = false,
      maxResources = 100,
      filter = Filter(include = Everything, exclude = Nothing)))
      
  val tr = Job(
    createdOn = DateTime.now.plus(1000),
    name = "TR",
    creatorId = bertails.id,
    organizationId = w3c.id,
    strategy = Strategy(
      entrypoint = URL("http://www.w3.org/TR"),
      linkCheck = false,
      maxResources = 100,
      filter=Filter.includePrefixes("http://www.w3.org/TR")))
        
  val ibm = Job(
    createdOn = DateTime.now.plus(2000),
    name = "IBM",
    creatorId = bertails.id,
    organizationId = w3c.id,
    strategy = Strategy(
      entrypoint = URL("http://www.ibm.com"),
      linkCheck = false,
      maxResources = 100,
      filter = Filter(include=Everything, exclude=Nothing)))
    
  val lemonde = Job(
    createdOn = DateTime.now.plus(3000),
    name = "Le Monde",
    creatorId = bertails.id,
    organizationId = w3c.id,
    strategy = Strategy(
      entrypoint = URL("http://www.lemonde.fr"),
      linkCheck = false,
      maxResources = 100,
      filter = Filter(include = Everything, exclude = Nothing)))
  
  tgambet.save()
  bertails.save()
  w3c.save()
  w3.save()
  tr.save()
  ibm.save()
  lemonde.save()
      
  override def onStart(app: Application): Unit = {

    org.w3.vs.assertor.CSSValidator.start()

  }
  
  override def onStop(app: Application): Unit = {
    org.w3.vs.assertor.CSSValidator.stop()
    system.shutdown()
  }
  
}
