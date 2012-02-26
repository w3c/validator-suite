package controllers

// TODO Documentation

abstract trait Composable[Req, Res] {
  val arity: Int
  // This must be a sequence of size = /arity/. How to enforce? Tuples would be ideal but don't manipulate well in an abstract way.
  def map(req: Req): List[Option[_]]
  implicit def onFail(req: Req): Res
}
object Composable {
  def composeMaps[Req, Res](l: Composable[Req, Res], r: Composable[Req, Res]): Req => List[Option[_]] = {
    if (l.arity == 0) req => r.map(req)
    else req => List(l.map(req), r.map(req)).flatten // Should call l.map, check if contains None, if so fill list with None r.arity times ...
  }
  def composeFails[Req, Res](l: Composable[Req, Res], r: Composable[Req, Res]): Req => Res = {
    req => l.map(req).dropRight(r.arity) match { // ... unless we take the first l.arity terms instead of dropingRight
      case _: List[Some[_]] => r.onFail(req)
      case _ => l.onFail(req)
    }
  }
}
object Composable0 {
  def apply[Req, Res](l: Composable0[Req, Res], r: Composable0[Req, Res]): Composable0[Req, Res] =
    new Composable0[Req, Res] {
      def condition(req: Req) = l.condition(req) && r.condition(req)
      def onFail(req: Req) = Composable.composeFails(l, r)(req)
    }
}
object Composable1 {
  def apply[Req, Res, A](l: Composable[Req, Res], r: Composable[Req, Res]): Composable1[Req, Res, A] =
    new Composable1[Req, Res, A] {
      def map(req: Req) = Composable.composeMaps(l, r)(req)
      def onFail(req: Req) = Composable.composeFails(l, r)(req)
    }
}
object Composable2 {
  def apply[Req, Res, A, B](l: Composable[Req, Res], r: Composable[Req, Res]): Composable2[Req, Res, A, B] =
    new Composable2[Req, Res, A, B] {
      def map(req: Req) = Composable.composeMaps(l, r)(req)
      def onFail(req: Req) = Composable.composeFails(l, r)(req)
    }
}
object Composable3 {
  def apply[Req, Res, A, B, C](l: Composable[Req, Res], r: Composable[Req, Res]): Composable3[Req, Res, A, B, C] =
    new Composable3[Req, Res, A, B, C] {
      def map(req: Req) = Composable.composeMaps(l, r)(req)
      def onFail(req: Req) = Composable.composeFails(l, r)(req)
    }
}
object Composable4 {
  def apply[Req, Res, A, B, C, D](l: Composable[Req, Res], r: Composable[Req, Res]): Composable4[Req, Res, A, B, C, D] =
    new Composable4[Req, Res, A, B, C, D] {
      def map(req: Req) = Composable.composeMaps(l, r)(req)
      def onFail(req: Req) = Composable.composeFails(l, r)(req)
    }
}

trait Composable0[Req, Res] extends Composable[Req, Res] {
  val arity = 0
  def condition(req: Req): Boolean
  override def map(req: Req): List[Option[_]] = condition(req) match {
    case true => List(Option(true))
    case false => List(None)
  }
  def apply(req: Req)(f: => Req => Res)(implicit onFail: Req => Res): Res =
    map(req) match {
      case a: List[Some[_]] => f(req)
      case _ => onFail(req)
    }
  def >> (r: Composable0[Req, Res]): Composable0[Req, Res] = Composable0[Req, Res](this, r)
  def >>[A] (r: Composable1[Req, Res, A]): Composable1[Req, Res, A] = Composable1[Req, Res, A](this, r)
  def >>[A, B] (r: Composable2[Req, Res, A, B]): Composable2[Req, Res, A, B] = Composable2[Req, Res, A, B](this, r)
  def >>[A, B, C] (r: Composable3[Req, Res, A, B, C]): Composable3[Req, Res, A, B, C] = Composable3[Req, Res, A, B, C](this, r)
  def >>[A, B, C, D] (r: Composable4[Req, Res, A, B, C, D]): Composable4[Req, Res, A, B, C, D] = Composable4[Req, Res, A, B, C, D](this, r)
}
trait Composable1[Req, Res, A] extends Composable[Req, Res] {
  val arity = 1
  def apply[A](req: Req)(f: => Req => A => Res)(implicit onFail: Req => Res): Res =
    map(req).head match {
      case Some(a) => f(req)(a.asInstanceOf[A])
      case _ => onFail(req)
    }
  def >> (r: Composable0[Req, Res]): Composable1[Req, Res, A] = Composable1[Req, Res, A](this, r)
  def >>[B] (r: Composable1[Req, Res, B]): Composable2[Req, Res, A, B] = Composable2[Req, Res, A, B](this, r)
  def >>[B, C] (r: Composable2[Req, Res, B, C]): Composable3[Req, Res, A, B, C] = Composable3[Req, Res, A, B, C](this, r)
  def >>[B, C, D] (r: Composable3[Req, Res, B, C, D]): Composable4[Req, Res, A, B, C, D] = Composable4[Req, Res, A, B, C, D](this, r)
}
trait Composable2[Req, Res, A, B] extends Composable[Req, Res] {
  val arity = 2
  def apply[A, B](req: Req)(f: => Req => A => B => Res)(implicit onFail: Req => Res): Res =
    map(req) match {
      // Type erasure sucks as hell. XXX workaround?
      case List(Some(a), Some(b)) => f(req)(a.asInstanceOf[A])(b.asInstanceOf[B])
      case _ => onFail(req)
    }
  def >> (r: Composable0[Req, Res]): Composable2[Req, Res, A, B] = Composable2[Req, Res, A, B](this, r)
  def >>[C] (r: Composable1[Req, Res, C]): Composable3[Req, Res, A, B, C] = Composable3[Req, Res, A, B, C](this, r)
  def >>[C, D] (r: Composable2[Req, Res, C, D]): Composable4[Req, Res, A, B, C, D] = Composable4[Req, Res, A, B, C, D](this, r)
}
trait Composable3[Req, Res, A, B, C] extends Composable[Req, Res] {
  val arity = 3
  def apply[A, B, C](req: Req)(f: => Req => A => B => C => Res)(implicit onFail: Req => Res): Res =
    map(req) match {
      case List(Some(a), Some(b), Some(c)) => f(req)(a.asInstanceOf[A])(b.asInstanceOf[B])(c.asInstanceOf[C])
      case _ => onFail(req)
    }
  def >> (r: Composable0[Req, Res]): Composable3[Req, Res, A, B, C] = Composable3[Req, Res, A, B, C](this, r)
  def >>[D] (r: Composable1[Req, Res, D]): Composable4[Req, Res, A, B, C, D] = Composable4[Req, Res, A, B, C, D](this, r)
}
trait Composable4[Req, Res, A, B, C, D] extends Composable[Req, Res] {
  val arity = 4
  def apply[A, B, C, D](req: Req)(f: => Req => A => B => C => D => Res)(implicit onFail: Req => Res): Res =
    map(req) match {
      case List(Some(a), Some(b), Some(c), Some(d)) => f(req)(a.asInstanceOf[A])(b.asInstanceOf[B])(c.asInstanceOf[C])(d.asInstanceOf[D])
      case _ => onFail(req)
    }
  def >> (r: Composable0[Req, Res]): Composable4[Req, Res, A, B, C, D] = Composable4[Req, Res, A, B, C, D](this, r)
}

