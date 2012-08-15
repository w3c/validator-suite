package org.w3.vs.store

import org.w3.vs.model._
import org.w3.banana._
import scalaz._
import org.joda.time.DateTime
import org.w3.util._
import org.w3.vs._
import diesel._
import ops._

object Binders extends Binders

trait Binders extends UriBuilders with LiteralBinders {

  val anyURI = xsd("anyURI")

  /* ontology definition */

  object ont extends PrefixBuilder("ont", "https://validator.w3.org/suite/ontology#")(ops) {

//    val assertionClasses = classUrisFor[Assertion](ont("Assertion"))
//    val contextClasses = classUrisFor[](ont("Context"))
//    val assertorResultClasses = classUrisFor[](ont("AssertorResult"))
//    val jobClasses = classUrisFor[](ont("Job"))
//    val jobDataClasses = classUrisFor[](ont("JobData"))
//    val organizationClasses = classUrisFor[](ont("Organization"))
//    val resourceResponseClasses = classUrisFor[](ont("ResourceResponse"))
//    val errorResponseClasses = classUrisFor[](ont("ErrorResponse"))
//    val httpResponseClasses = classUrisFor[](ont("HttpResponse"))
//    val runClasses = classUrisFor[](ont("Run"))
//    val strategyClasses = classUrisFor[](ont("Strategy"))
//    val userClasses = classUrisFor[](ont("User"))
//    val assertorSelectorClasses = classUrisFor[](ont("AssertorSelector")

    lazy val url = property[URL](apply("url"))
    lazy val lang = property[String](apply("lang"))
    lazy val title = property[String](apply("title"))
    lazy val severity = property[AssertionSeverity](apply("severity"))
    lazy val description = property[Option[String]](apply("description"))

    lazy val content = property[String](apply("content"))
    lazy val line = property[Option[Int]](apply("line"))
    lazy val column = property[Option[Int]](apply("column"))
    lazy val assertion = property[Assertion](apply("assertion"))
    lazy val assertions = set[Assertion](apply("assertion"))

    lazy val job = property[(OrganizationId, JobId)](apply("job"))
    lazy val run = property[(OrganizationId, JobId, RunId)](apply("run"))
    lazy val assertor = property[AssertorId](apply("assertor"))
    lazy val createdAt = property[DateTime](apply("createdAt"))
    lazy val timestamp = property[DateTime](apply("timestamp"))
    lazy val user = property[UserVO](apply("user"))

    lazy val name = property[String](apply("name"))
    lazy val creator = property[UserId](apply("creator"))
    lazy val organization = property[OrganizationId](apply("organization"))
    lazy val organizationOpt = optional[OrganizationId](apply("organization"))
    lazy val strategy = property[Strategy](apply("strategy"))(StrategyBinder)

    lazy val resources = property[Int](apply("resources"))
    lazy val errors = property[Int](apply("errors"))
    lazy val warnings = property[Int](apply("warnings"))

    lazy val admin = property[UserId](apply("admin"))

    lazy val action = property[HttpAction](apply("action"))
    lazy val why = property[String](apply("why"))
    lazy val status = property[Int](apply("status"))
    lazy val headers = property[Headers](apply("headers"))
    lazy val urls = property[List[URL]](apply("urls"))

    lazy val explorationMode = property[ExplorationMode](apply("explorationMode"))
    lazy val completedAt = property[Option[DateTime]](apply("completedAt"))

    lazy val linkCheck = property[Boolean](apply("linkCheck"))
    lazy val maxResources = property[Int](apply("maxResources"))
    lazy val assertorSelector = property[AssertorSelector](apply("assertorSelector"))
    
    lazy val email = property[String](apply("email"))
    lazy val password = property[String](apply("password"))

    lazy val map = property[Map[String, List[String]]](apply("map"))
    lazy val runUri = property[Rdf#URI](apply("run"))
    lazy val contexts = property[List[Context]](apply("contexts"))
    lazy val resourceResponse = set[ResourceResponse](apply("resourceResponse"))
    
  }

  /* binders for entities */

  implicit lazy val ContextBinder: PointedGraphBinder[Rdf, Context] = pgb[Context](ont.content, ont.line, ont.column)(Context.apply, Context.unapply)

  implicit lazy val AssertionBinder: PointedGraphBinder[Rdf, Assertion] = pgb[Assertion](ont.url, ont.assertor, ont.contexts, ont.lang, ont.title, ont.severity, ont.description, ont.timestamp)(Assertion.apply, Assertion.unapply)

  implicit lazy val JobVOBinder = pgbWithId[JobVO]("#thing")(ont.name, ont.timestamp, ont.strategy, ont.creator, ont.organization)(JobVO.apply, JobVO.unapply)

  implicit lazy val OrganizationVOBinder = pgbWithId[OrganizationVO]("#thing")(ont.name, ont.admin)(OrganizationVO.apply, OrganizationVO.unapply)

  implicit lazy val ErrorResponseBinder = pgb[ErrorResponse](ont.url, ont.action, ont.timestamp, ont.why)(ErrorResponse.apply, ErrorResponse.unapply)

  implicit lazy val HttpResponseBinder = pgb[HttpResponse](ont.url, ont.action, ont.timestamp, ont.status, ont.headers, ont.urls)(HttpResponse.apply, HttpResponse.unapply)


  implicit lazy val ResourceResponseBinder = new PointedGraphBinder[Rdf, ResourceResponse] {

    def toPointedGraph(t: ResourceResponse): PointedGraph[Rdf] = t match {
      case e @ ErrorResponse(_, _, _, _) => ErrorResponseBinder.toPointedGraph(e)
      case h @ HttpResponse(_, _, _, _, _, _) => HttpResponseBinder.toPointedGraph(h)
    }

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ResourceResponse] = {
      ErrorResponseBinder.fromPointedGraph(pointed) orElse HttpResponseBinder.fromPointedGraph(pointed)
    }

  }

  implicit lazy val RunBinder: PointedGraphBinder[Rdf, Run] = new PointedGraphBinder[Rdf, Run] {

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, Run] = {
//      println("**********")
//      println(pointed.pointer)
//      org.w3.banana.jena.JenaUtil.dump(pointed.graph)
//      println("**********")
      for {
        id <- (pointed / ont.run).as[(OrganizationId, JobId, RunId)]
        strategy <- (pointed / ont.strategy).as[Strategy]
        createdAt <- (pointed / ont.createdAt).as[DateTime]
        completedAt <- (pointed / ont.completedAt).asOption[DateTime]
        explorationMode <- (pointed / ont.explorationMode).asOption[ExplorationMode] // <- WRONG
        assertions <- (pointed / ont.assertion).asSet[Assertion]
        rrs <- (pointed / ont.resourceResponse).asSet[ResourceResponse]
      } yield {
        var run = Run(id = id, strategy = strategy, createdAt = createdAt)
        run = run.withAssertions(assertions)
        rrs foreach { rr =>
          rr match {
            case HttpResponse(_, _, _, _, _, urls) => run = run.withNewUrlsToBeExplored(urls)._1
            case _ => ()
          }
        }
        completedAt foreach { at => run = run.completedAt(at) }
        run
      }
    }

    def toPointedGraph(run: Run): PointedGraph[Rdf] = (
      ops.makeUri("#thing")
      -- ont.run ->- run.id.toUri
      -- ont.strategy ->- run.strategy
      -- ont.createdAt ->- run.createdAt
    )

  }

 //pgbWithId[Run]("#thing")(ont.run, ont.strategy, ont.explorationMode, ont.createdAt, ont.assertions, ont.completedAt, ont.timestamp, ont.resources, ont.errors, ont.warnings)(RunVO.apply, RunVO.unapply)

  implicit lazy val UserVOBinder = pgbWithId[UserVO]("#me")(ont.name, ont.email, ont.password, ont.organizationOpt)(UserVO.apply, UserVO.unapply)

  implicit lazy val AssertorSelectorBinder = pgb[AssertorSelector](ont.name, ont.map)(AssertorSelector.apply, AssertorSelector.unapply)

  // works only for Filter(include = Everything, exclude = Nothing) for the moment
  implicit lazy val StrategyBinder: PointedGraphBinder[Rdf, Strategy] =
    pgb[Strategy](ont.url, ont.linkCheck, ont.maxResources, ont.assertorSelector)(
      { (url, lc, maxR, as) => Strategy(url, lc, maxR, Filter.includeEverything, as) },
      { s => Some((s.entrypoint, s.linkCheck, s.maxResources, s.assertorSelector)) })

}
