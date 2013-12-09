package org.w3.vs

import com.codahale.metrics._
import org.w3.vs.model.{AssertorId, CreditPlan, Plan}
import java.util.concurrent.TimeUnit

object Metrics {

  val metrics = new MetricRegistry()

  /**
   * controllers    (timers)
   *    front
   *       index
   *       try
   *       buy
   *       faqs
   *       logos
   *       pricing
   *       features
   *       terms
   *       privacy
   *       login
   *       register
   *    back
   *       account
   *       jobs
   *       newJob
   *       report.resource
   *       report.resources
   *       report.messages
   *    form
   *       login
   *       login.failures        (meter)
   *       logout
   *       register
   *       register.failures     (meter)
   *       editAccount
   *       editAccount.failures  (meter)
   *       editPassword
   *       editPassword.failures (meter)
   *       createJob
   *       createJob.failures    (meter)
   *       fsCallback
   *    errors
   *       e400
   *       e404
   *       e500
   *
   **/
  def forController(name: String): Timer = metrics.timer(s"controllers.${name}")
  object errors {
    def e400() { metrics.meter(s"controllers.errors.e400").mark() }
    def e404() { metrics.meter(s"controllers.errors.e404").mark() }
    def e500() { metrics.meter(s"controllers.errors.e500").mark() }
  }
  object form {
    def loginFailure() { metrics.meter(s"controllers.form.login.failures").mark() }
    def registerFailure() { metrics.meter(s"controllers.form.register.failures").mark() }
    def createJobFailure() { metrics.meter(s"controllers.form.createJob.failures").mark() }
    def editAccountFailure() { metrics.meter(s"controllers.form.editAccount.failures").mark() }
    def editPasswordFailure() { metrics.meter(s"controllers.form.editPassword.failures").mark() }
  }

  /**
   *  crawler
   *    pending         (count)
   *    calls           (timer)
   *    calls.failures  (meter)
   */
  object crawler {
    val pending = metrics.counter("crawler.pending")
    def time() = metrics.timer("crawler.calls").time()
    def failure() { metrics.meter("crawler.calls.failure").mark() }
  }

  /**
   * assertors.<id>
   *       pending              (count)
   *       calls                (timer)
   *       calls.failures       (meter)
   *       errors               (histogram)
   *       warnings             (histogram)
   */
  object assertors {
    def pending(id: AssertorId) = metrics.counter(s"assertors.${id.id}.pending")
    def time(id: AssertorId) = metrics.timer(s"assertors.${id.id}.calls").time()
    def failure(id: AssertorId) { metrics.meter(s"assertors.${id.id}.calls.failures").mark() }
    def errors(id: AssertorId, int: Int)   { metrics.histogram(s"assertors.${id.id}.errors").update(int) }
    def warnings(id: AssertorId, int: Int) { metrics.histogram(s"assertors.${id.id}.warnings").update(int) }
  }

  /**
   * jobs
   *    count     (gauge)   TODO
   *    running   (count)
   *    runs      (timer)
   *    resources (histogram)
   *    errors    (histogram)
   *    warnings  (histogram)
   */
  object jobs {
    val running = metrics.counter("jobs.running")
    def time() = metrics.timer("jobs.runs").time()
    def errors(int: Int)    { metrics.histogram("jobs.errors").update(int) }
    def warnings(int: Int)  { metrics.histogram("jobs.warnings").update(int) }
    def resources(int: Int) { metrics.histogram("jobs.resources").update(int) }
  }

  /**
   * purchases
   *    redirect                    (meter)
   *    callback                    (meter)
   *    callback.failure            (meter)
   *    plan.<name>                 (meter)
   *    credits                     (meter)
   *    eurosEquiv                  (meter)
   */
  object purchases {
    def redirect()        { metrics.meter("purchases.redirect").mark() }
    def callback()        { metrics.meter("purchases.callback").mark() }
    def callbackFailure() { metrics.meter("purchases.callback.failure").mark() }

    def purchased(plan: Plan) {
      metrics.meter(s"purchases.plan.${plan.fastSpringKey}").mark()
      plan match {
        case p: CreditPlan =>
          metrics.meter(s"purchases.credits").mark(p.credits)
        case _ => ()
      }
    }
  }

  object db {
    import org.w3.vs.util.timer._
    import org.w3.vs.model._
    def users()(implicit vs: ValidatorSuite with Database) = new CachedGauge[Int](1, TimeUnit.MINUTES) {
      def loadValue(): Int = User.getCount().getOrFail()
    }
    def jobs()(implicit vs: ValidatorSuite with Database) = new CachedGauge[Int](1, TimeUnit.MINUTES) {
      def loadValue(): Int = Job.getCount().getOrFail()
    }
  }

}
