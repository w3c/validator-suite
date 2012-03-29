package org.w3.vs.actor

import java.util.UUID
import org.w3.vs.model._
import org.w3.vs._
import akka.dispatch._
import akka.actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask

object Organization {

  // def get(organizationId: OrganizationId)(implicit configuration: VSConfiguration): Future[Option[Organization]] = {
  //   def context = configuration.system
  //   val organizationsRef = context.actorFor(context / "user" / "organizations")
  //   implicit val timeout: Timeout = 5.second
  //   (organizationsRef ? GetOrganization(organizationId)).mapTo[Option[ActorRef]] map { refOpt => refOpt map { new Organization(organizationId, _) } }
  // }

}

class Organization(id: OrganizationId, organizationRef: ActorRef) {

  // def getJob(jobId: JobId)(implicit configuration: VSConfiguration): Future[Option[Job]] = {
  //   def context = configuration.system
  //   implicit val timeout: Timeout = 5.seconds
  //   (organizationRef ? GetJob(jobId)).mapTo[Option[ActorRef]] map { refOpt => refOpt map { new Job(jobId, _) } }
  // }

}
