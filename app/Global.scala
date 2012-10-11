import play.api._

import java.net.ResponseCache

object Global extends GlobalSettings {

  val conf = org.w3.vs.Prod.configuration
  import conf._

  override def onStart(app: Application): Unit = {
    conf.httpCacheOpt foreach { cache => ResponseCache.setDefault(cache) }
    org.w3.vs.assertor.LocalValidators.start()
  }
  
  override def onStop(app: Application): Unit = {
    ResponseCache.setDefault(null)
    org.w3.vs.assertor.LocalValidators.stop()
    store.shutdown()
    system.shutdown()
  }
  
}
