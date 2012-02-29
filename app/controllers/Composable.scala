package controllers

trait Composable[A, Req, Res, Z] {
  def extract(req: Req): Either[Res, A]
  def apply(f: Req => A => Res)(implicit partial: (Req => Res) => Z): Z =
    partial { Composable.semantics(this)(f) _ }
}

object Composable {
    
  def semantics[A, Req, Res, Z](c: Composable[A, Req, Res, Z])(f: Req => A => Res)(req: Req): Res = {
    c.extract(req).fold(fail => fail, a => f(req)(a))
  }
  def semantics[A, B, Req, Res, Z](c1: Composable[A, Req, Res, Z], c2: Composable[B, Req, Res, Z])(f: Req => A => B => Res)(req: Req): Res = {
    c1.extract(req).fold(fail => fail, a => {
      c2.extract(req).fold(fail => fail, b => f(req)(a)(b))
    })
  }
  def semantics[A, B, C, Req, Res, Z](c1: Composable[A, Req, Res, Z], c2: Composable[B, Req, Res, Z], c3: Composable[C, Req, Res, Z])(f: Req => A => B => C => Res)(req: Req): Res = {
    c1.extract(req).fold(fail => fail, a => {
      c2.extract(req).fold(fail => fail, b => {
        c3.extract(req).fold(fail => fail, c => f(req)(a)(b)(c))
      })
    })
  }
  def semantics[A, B, C, D, Req, Res, Z](c1: Composable[A, Req, Res, Z], c2: Composable[B, Req, Res, Z], c3: Composable[C, Req, Res, Z], c4: Composable[D, Req, Res, Z])(f: Req => A => B => C => D => Res)(req: Req): Res = {
    c1.extract(req).fold(fail => fail, a => {
      c2.extract(req).fold(fail => fail, b => {
        c3.extract(req).fold(fail => fail, c => {
          c4.extract(req).fold(fail => fail, d => f(req)(a)(b)(c)(d))
        })
      })
    })
  }
  def semantics[A, B, C, D, E, Req, Res, Z](c1: Composable[A, Req, Res, Z], c2: Composable[B, Req, Res, Z], c3: Composable[C, Req, Res, Z], c4: Composable[D, Req, Res, Z], c5: Composable[E, Req, Res, Z])(f: Req => A => B => C => D => E => Res)(req: Req): Res = {
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
  
  implicit def compo2[A, B, Req, Res, Z](t:(Composable[A, Req, Res, Z], Composable[B, Req, Res, Z])) = {
    val (a, b) = t
    new Object {
      def apply(f: Req => A => B => Res)(implicit partial: (Req => Res) => Z): Z = partial {Composable.semantics(a, b)(f) _}
    }
  }
  implicit def compo3[A, B, C, Req, Res, Z](t:(Composable[A, Req, Res, Z], Composable[B, Req, Res, Z], Composable[C, Req, Res, Z]))(implicit partial: (Req => Res) => Z) = {
    val (a, b, c) = t
    new Object {
      def apply(f: Req => A => B => C => Res): Z = partial {Composable.semantics(a, b, c)(f) _}
    }
  }
  implicit def compo4[A, B, C, D, Req, Res, Z](t:(Composable[A, Req, Res, Z], Composable[B, Req, Res, Z], Composable[C, Req, Res, Z], Composable[D, Req, Res, Z]))(implicit partial: (Req => Res) => Z) = {
    val (a, b, c, d) = t
    new Object {
      def apply(f: Req => A => B => C => D => Res): Z = partial {Composable.semantics(a, b, c, d)(f) _}
    }
  }
  implicit def compo5[A, B, C, D, E, Req, Res, Z](t:(Composable[A, Req, Res, Z], Composable[B, Req, Res, Z], Composable[C, Req, Res, Z], Composable[D, Req, Res, Z], Composable[E, Req, Res, Z]))(implicit partial: (Req => Res) => Z) = {
    val (a, b, c, d, e) = t
    new Object {
      def apply(f: Req => A => B => C => D => E => Res): Z = partial {Composable.semantics(a, b, c, d, e)(f) _}
    }
  }
}
