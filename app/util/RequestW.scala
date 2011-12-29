package org.w3.util

import dispatch._
import org.w3.vs._

class RequestW(req:Request) {

  def post(body:String):Request =
    (req <<< body).copy(method="POST")
  
  def head:Request = req.copy(method="HEAD")
    
  def put(body:String):Request = req <<< body
    
  def getStatus:Handler[Int] = new Handler(req, (c, r, e) => c, { case t => () })

  def getHeaders: Handler[Headers] = req >:> { h => h.mapValues(_.toList) }

  def getHeader(header:String):Handler[String] = req >:> { _(header).head }
  
  def get:Request = req.copy(method="GET")
    
  def >>+ [A, B] (block: Request => (Handler[A], Handler[B])) = {
    Handler(req, { (code, res, opt_ent) =>
      val (a, b) = block( /\ )
        (a.block(code, res, opt_ent), b.block(code,res,opt_ent))
    } )
  }

  def >>++ [A, B, C] (block: Request => (Handler[A], Handler[B], Handler[C])) = {
    Handler(req, { (code, res, opt_ent) =>
      val (a, b, c) = block( /\ )
        (a.block(code, res, opt_ent), b.block(code,res,opt_ent), c.block(code,res,opt_ent))
    } )
  }

}
