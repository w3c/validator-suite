package controllers

import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.mvc.Result
import Composable._
import org.w3.vs.model.User
import play.api.mvc.Results
import org.w3.vs.prod.configuration.store
import play.api.mvc.Action

trait Composable[A] {
  def extract(req: Req): Either[Result, A]
  def apply(f: Req => A => Result) =
    Action {Composable.semantics(this)(f) _} 
}

object Composable {
  type Req = Request[AnyContent]
  
  def semantics[A](c: Composable[A])(f: Req => A => Result)(req: Req): Result = {
    c.extract(req).fold(fail => fail, a => f(req)(a))
  }
  def semantics[A, B](c1: Composable[A], c2: Composable[B])(f: Req => A => B => Result)(req: Req): Result = {
    c1.extract(req).fold(fail => fail, a => {
      c2.extract(req).fold(fail => fail, b => f(req)(a)(b))
    })
  }
  def semantics[A, B, C](c1: Composable[A], c2: Composable[B], c3: Composable[C])(f: Req => A => B => C => Result)(req: Req): Result = {
    c1.extract(req).fold(fail => fail, a => {
      c2.extract(req).fold(fail => fail, b => {
        c3.extract(req).fold(fail => fail, c => f(req)(a)(b)(c))
      }) 
    })
  }
  def semantics[A, B, C, D](c1: Composable[A], c2: Composable[B], c3: Composable[C], c4: Composable[D])(f: Req => A => B => C => D => Result)(req: Req): Result = {
    c1.extract(req).fold(fail => fail, a => {
      c2.extract(req).fold(fail => fail, b => {
        c3.extract(req).fold(fail => fail, c => {
          c4.extract(req).fold(fail => fail, d => f(req)(a)(b)(c)(d))
        }) 
      }) 
    })
  }
  def semantics[A, B, C, D, E](c1: Composable[A], c2: Composable[B], c3: Composable[C], c4: Composable[D], c5: Composable[E])(f: Req => A => B => C => D => E => Result)(req: Req): Result = {
    c1.extract(req).fold(fail => fail, a => {
      c2.extract(req).fold(fail => fail, b => {
        c3.extract(req).fold(fail => fail, c => {
          c4.extract(req).fold(fail => fail, d => {
            c5.extract(req).fold(fail => fail, e => f(req)(a)(b)(c)(d)(e))
          })
        })
      }) 
    })
  }
  
  implicit def compo2[A, B](t:(Composable[A], Composable[B])) = {
    val (a, b) = t
    new Object {
      def apply(f: Req => A => B => Result): Action[AnyContent] = Action {Composable.semantics(a, b)(f) _}
    }
  }
  implicit def compo3[A, B, C](t:(Composable[A], Composable[B], Composable[C])) = {
    val (a, b, c) = t
    new Object {
      def apply(f: Req => A => B => C => Result) = Action {Composable.semantics(a, b, c)(f) _}
    }
  }
  implicit def compo4[A, B, C, D](t:(Composable[A], Composable[B], Composable[C], Composable[D])) = {
    val (a, b, c, d) = t
    new Object {
      def apply(f: Req => A => B => C => D => Result) = Action {Composable.semantics(a, b, c, d)(f) _}
    }
  }
  implicit def compo5[A, B, C, D, E](t:(Composable[A], Composable[B], Composable[C], Composable[D], Composable[E])) = {
    val (a, b, c, d, e) = t
    new Object {
      def apply(f: Req => A => B => C => D => E => Result) = Action {Composable.semantics(a, b, c, d, e)(f) _}
    }
  }
}

object IfAjax extends Composable[Boolean] {
  def extract(req: Req) = {
    req.headers.get("x-requested-with") match {
      case Some("xmlhttprequest") => Right(true)
      case _ => Left(play.api.mvc.Results.BadRequest("This request can only be an Ajax request"))
    }
  }
}
object IfNotAjax extends Composable[Boolean] {
  def extract(req: Req) = {
    req.headers.get("x-requested-with") match {
      case None => Right(true)
      case _ => Left(play.api.mvc.Results.BadRequest("This request cannot be an Ajax request"))
    }
  }
}
object OptionAjax extends Composable[Option[Boolean]] {
  def extract(req: Req) = {
    req.headers.get("x-requested-with") match {
      case Some("xmlhttprequest") => Right(Some(true))
      case _ => Right(Some(false))
    }
  }
}

object IfAuth extends Composable[User] {
  def extract(req: Req) = {
    req.session.get("email").flatMap{store.getUserByEmail(_).right.get} match {
      case Some(user) => Right(user)
      case _ => Left(Results.Redirect(controllers.routes.Application.login))
    }
  }
}
object IfNotAuth extends Composable[Boolean] {
  def extract(req: Req) = {
    req.session.get("email") match { 
      case None => Right(true)
      case _ => Left(Results.Redirect(controllers.routes.Validator.index)) 
    }
  }
}
object OptionAuth extends Composable[Option[User]] {
  def extract(req: Req) = {
    Right(req.session.get("email").flatMap{store.getUserByEmail(_).right.get})
  }
}
