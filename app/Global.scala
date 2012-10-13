import play.api._

import java.net.ResponseCache
import com.yammer.metrics._
import com.yammer.metrics.reporting._
import java.util.concurrent.TimeUnit

object Global extends GlobalSettings {

  val conf = org.w3.vs.Prod.configuration
  import conf._

  override def onStart(app: Application): Unit = {
    // ConsoleReporter.enable(10, TimeUnit.SECONDS)
    conf.httpCacheOpt foreach { cache => ResponseCache.setDefault(cache) }
    org.w3.vs.assertor.LocalValidators.start()
  }
  
  override def onStop(app: Application): Unit = {
    Metrics.shutdown()
    ResponseCache.setDefault(null)
    org.w3.vs.assertor.LocalValidators.stop()
    store.shutdown()
    system.shutdown()
  }
  
}
