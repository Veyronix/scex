package com.avsystem.scex

import java.{lang => jl, util => ju}

import scala.util.control.NoStackTrace

/**
  * Created: 16-06-2014
  * Author: ghik
  */
case class EvaluationException(cause: Throwable) extends RuntimeException(cause) with NoStackTrace
