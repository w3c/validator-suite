package controllers

import play.api.mvc.Result
import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.mvc.Action

abstract trait ActionModule extends Composable[Request[AnyContent], Result] {
  type Req = Request[AnyContent]
  type Res = Result
  implicit def onFail(req: Request[AnyContent]): Result = 
    play.api.mvc.Results.InternalServerError("ActionModule " + this.getClass.toString + " failed to map its parameters.")
}

// TODO Check whether there is another way to make a Composable0 castable to ActionModule0 implicitly
object ActionModule {
  implicit def fromComposable0(c: Composable0[Request[AnyContent], Result]): ActionModule0 = {
    new ActionModule0 {
      override def condition(req: Request[AnyContent]) = c.condition(req)
      override def onFail(req: Request[AnyContent]) = c.onFail(req)
    }
  }
  implicit def fromComposable1[A](c: Composable1[Request[AnyContent], Result, A]): ActionModule1[A] = {
    new ActionModule1[A] {
      override def map(req: Request[AnyContent]) = c.map(req)
      override def onFail(req: Request[AnyContent]) = c.onFail(req)
    }
  }
  implicit def fromComposable2[A, B](c: Composable2[Request[AnyContent], Result, A, B]): ActionModule2[A, B] = {
    new ActionModule2[A, B] {
      override def map(req: Request[AnyContent]) = c.map(req)
      override def onFail(req: Request[AnyContent]) = c.onFail(req)
    }
  }
  implicit def fromComposable3[A, B, C](c: Composable3[Request[AnyContent], Result, A, B, C]): ActionModule3[A, B, C] = {
    new ActionModule3[A, B, C] {
      override def map(req: Request[AnyContent]) = c.map(req)
      override def onFail(req: Request[AnyContent]) = c.onFail(req)
    }
  }
  implicit def fromComposable4[A, B, C, D](c: Composable4[Request[AnyContent], Result, A, B, C, D]): ActionModule4[A, B, C, D] = {
    new ActionModule4[A, B, C, D] {
      override def map(req: Request[AnyContent]) = c.map(req)
      override def onFail(req: Request[AnyContent]) = c.onFail(req)
    }
  }
}

trait ActionModule0 extends Composable0[Request[AnyContent], Result] with ActionModule {
  def apply(f: => Req => Res)(implicit onFail: Req => Res = onFail): Action[AnyContent] = 
    Action { req => super.apply(req)(f) }
  def >> (r: ActionModule0): ActionModule0 = super.>>(r)
  def >>[A] (r: ActionModule1[A]): ActionModule1[A] = super.>>(r)
  def >>[A, B] (r: ActionModule2[A, B]): ActionModule2[A, B] = super.>>(r)
  def >>[A, B, C] (r: ActionModule3[A, B, C]): ActionModule3[A, B, C] = super.>>(r)
  def >>[A, B, C, D] (r: ActionModule4[A, B, C, D]): ActionModule4[A, B, C, D] = super.>>(r)
}
trait ActionModule1[A] extends Composable1[Request[AnyContent], Result, A] with ActionModule {
  import ActionModule._
  def apply[A](f: => Req => A => Res)(implicit onFail: Req => Res = onFail): Action[AnyContent] = 
    Action { req => super.apply[A](req)(f) }
  def >> (r: ActionModule0): ActionModule1[A] = super.>>(r)
  def >>[B] (r: ActionModule1[B]): ActionModule2[A, B] = super.>>(r)
  def >>[B, C] (r: ActionModule2[B, C]): ActionModule3[A, B, C] = super.>>(r)
  def >>[B, C, D] (r: ActionModule3[B, C, D]): ActionModule4[A, B, C, D] = super.>>(r)
}
trait ActionModule2[A, B] extends Composable2[Request[AnyContent], Result, A, B] with ActionModule {
  def apply[A, B](f: => Req => A => B => Res)(implicit onFail: Req => Res = onFail): Action[AnyContent] = 
    Action { req => super.apply[A, B](req)(f) }
  def >> (r: ActionModule0): ActionModule2[A, B] = super.>>(r)
  def >>[C] (r: ActionModule1[C]): ActionModule3[A, B, C] = super.>>(r)
  def >>[C, D] (r: ActionModule2[C, D]): ActionModule4[A, B, C, D] = super.>>(r)
}
trait ActionModule3[A, B, C] extends Composable3[Request[AnyContent], Result, A, B, C] with ActionModule {
  def apply[A, B, C](f: => Req => A => B => C => Res)(implicit onFail: Req => Res = onFail): Action[AnyContent] = 
    Action { req => super.apply[A, B, C](req)(f) }
  def >> (r: ActionModule0): ActionModule3[A, B, C] = super.>>(r)
  def >>[D] (r: ActionModule1[D]): ActionModule4[A, B, C, D] = super.>>(r)
}
trait ActionModule4[A, B, C, D] extends Composable4[Request[AnyContent], Result, A, B, C, D] with ActionModule {
  def apply[A, B, C, D](f: => Req => A => B => C => D => Res)(implicit onFail: Req => Res = onFail): Action[AnyContent] = 
    Action { req => super.apply[A, B, C, D](req)(f) }
  def >> (r: ActionModule0): ActionModule4[A, B, C, D] = super.>>(r)
}
