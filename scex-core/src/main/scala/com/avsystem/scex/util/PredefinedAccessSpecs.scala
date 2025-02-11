package com.avsystem.scex.util

import java.{lang => jl}

import com.github.ghik.silencer.silent

import scala.math.ScalaNumericConversions
import scala.runtime._

@silent("a pure expression does nothing in statement position")
object PredefinedAccessSpecs {

  import com.avsystem.scex.validation.SymbolValidator._

  val basicOperations = deny {
    // Methods on toplevel types
    on { any: Any =>
      any.equals _
      any.hashCode
      any.##
      any.asInstanceOf
      any.isInstanceOf
      any.getClass
    }
    on { anyRef: AnyRef =>
      anyRef.eq _
      anyRef.ne _
      anyRef.wait()
      anyRef.wait(_: Long)
      anyRef.wait(_: Long, _: Int)
      anyRef.notify()
      anyRef.notifyAll()
      anyRef.synchronized(_: Any)
    }
    on { ip: RangedProxy[Any@plus] =>
      ip.to(_: Any)
      ip.to(_: Any, _: Any)
      ip.until(_: Any)
      ip.until(_: Any, _: Any)
    }
    on { p: Proxy =>
      p.self
    }
    on { snac: ScalaNumericConversions =>
      snac.underlying()
    }
  } ++ allow {
    on { any: Any =>
      any == _
      any != _
      any + (_: String)
      any -> (_: Any)
    }
    on { anyRef: AnyRef =>
      anyRef == (_: AnyRef)
      anyRef != (_: AnyRef)
    }
    on { nul: Null =>
      nul.toString
    }
    Tuple2.apply _
    on { pair: (Any, Any) =>
      pair.swap
    }

    // Operations on primitive types
    on { u: Unit =>
      u.toString
      u.all.members
    }
    on { b: Boolean =>
      b.toString
      b.all.members
      b.implicitlyAs[RichBoolean].all.members
    }
    on { c: Char =>
      c.toString
      c.all.members
      c.implicitlyAs[RichChar].all.members
    }
    Char.char2double _
    Char.char2float _
    Char.char2int _
    Char.char2long _
    on { b: Byte =>
      b.toString
      b.all.members
      b.implicitlyAs[RichByte].all.members
    }
    Byte.byte2double _
    Byte.byte2float _
    Byte.byte2int _
    Byte.byte2long _
    Byte.byte2short _
    on { s: Short =>
      s.toString
      s.all.members
      s.implicitlyAs[RichShort].all.members
    }
    Short.short2double _
    Short.short2float _
    Short.short2int _
    Short.short2long _
    on { i: Int =>
      i.toString
      i.all.members
      i.implicitlyAs[RichInt].all.members
    }
    Int.int2double _
    Int.int2float _
    Int.int2long _
    on { l: Long =>
      l.toString
      l.all.members
      l.implicitlyAs[RichLong].all.members
    }
    Long.long2double _
    Long.long2float _
    on { f: Float =>
      f.toString
      f.all.members
      f.implicitlyAs[RichFloat].all.members
    }
    Float.float2double _
    on { d: Double =>
      d.toString
      d.all.members
      d.implicitlyAs[RichDouble].all.members
    }

    // Boxed Java primitive types
    on { b: jl.Boolean =>
      b.toString
      b.booleanValue
      b.compareTo _
    }
    on { c: jl.Character =>
      c.toString
      c.charValue
      c.compareTo _
    }
    on { b: jl.Byte =>
      b.toString
      b.compareTo _
      b.byteValue
      b.shortValue
      b.intValue
      b.longValue
      b.floatValue
      b.doubleValue
    }
    on { s: jl.Short =>
      s.toString
      s.compareTo _
      s.byteValue
      s.shortValue
      s.intValue
      s.longValue
      s.floatValue
      s.doubleValue
    }
    on { i: jl.Integer =>
      i.toString
      i.compareTo _
      i.byteValue
      i.shortValue
      i.intValue
      i.longValue
      i.floatValue
      i.doubleValue
    }
    on { l: jl.Long =>
      l.toString
      l.compareTo _
      l.byteValue
      l.shortValue
      l.intValue
      l.longValue
      l.floatValue
      l.doubleValue
    }
    on { f: jl.Float =>
      f.toString
      f.compareTo _
      f.byteValue
      f.shortValue
      f.intValue
      f.longValue
      f.floatValue
      f.doubleValue
    }
    on { d: jl.Double =>
      d.toString
      d.compareTo _
      d.byteValue
      d.shortValue
      d.intValue
      d.longValue
      d.floatValue
      d.doubleValue
      d.isNaN
      d.isInfinite
    }

    // Implicit conversions between primitive and boxed types
    Predef.Boolean2boolean _
    Predef.boolean2Boolean _
    Predef.Byte2byte _
    Predef.byte2Byte _
    Predef.Short2short _
    Predef.short2Short _
    Predef.Integer2int _
    Predef.int2Integer _
    Predef.Long2long _
    Predef.long2Long _
    Predef.Float2float _
    Predef.float2Float _
    Predef.Double2double _
    Predef.double2Double _
    Predef.$conforms

    DummyImplicit.dummyImplicit

    // Array operations
    on { a: Array[_] =>
      a.apply _
      a.length
    }

    // String related stuff
    StringContext.apply _
    on { sc: StringContext =>
      sc.all.membersNamed("s")
      sc.all.membersNamed("raw")
    }
    on { s: String =>
      s + (_: Any)
      s.charAt _
      s.compareTo _
      s.compareToIgnoreCase _
      s.concat _
      s.contains _
      s.endsWith _
      s.equalsIgnoreCase _
      s.indexOf(_: String)
      s.indexOf(_: String, _: Int)
      s.isEmpty
      s.lastIndexOf(_: String)
      s.lastIndexOf(_: String, _: Int)
      s.length
      s.matches _
      s.all.membersNamed.replace
      s.replaceAll _
      s.replaceAllLiterally(_: String, _: String)
      s.replaceFirst _
      s.all.membersNamed.split
      s.startsWith(_: String)
      s.all.membersNamed.substring
      s.toLowerCase
      s.toUpperCase
      s.toString
      s.trim
      s.implicitlyAs[Ordered[String]].all.members
      s.capitalize
      s.nonEmpty
      s.reverse
      s.stripLineEnd
      s.stripPrefix(_: String)
      s.stripSuffix(_: String)
      s.toBoolean
      s.toByte
      s.toShort
      s.toInt
      s.toLong
      s.toFloat
      s.toDouble
      s.filter(_: Char => Boolean)
      s.take(_: Int)
      s.takeRight(_: Int)
      s.takeWhile(_: Char => Boolean)
      s.drop(_: Int)
      s.dropRight(_: Int)
      s.dropWhile(_: Char => Boolean)
    }

    // Math functions
    math.`package`.all.members

    // Literal
    on { l: Literal =>
      l.all.introduced.members
    }
    Literal.all.introduced.members
  }

  val universalToString = allow {
    on { any: Any =>
      any.toString
    }
  }
}
