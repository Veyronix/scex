package com.avsystem.scex.compiler

import java.lang.reflect.{Constructor, Field, Member, Method}
import java.security.MessageDigest
import java.{lang => jl}

import scala.io.Codec
import scala.tools.nsc.Global

/**
 * Stuff to translate Symbols into their corresponding Java reflection artifacts. Code copied from
 * scala.reflect.runtime.JavaMirrors
 *
 * Created: 27-10-2014
 * Author: ghik
 */
trait SymbolErasures { this: Global =>

  def classLoader: ClassLoader

  import definitions._

  private val PackageAndClassPattern = """(.*\.)(.*)$""".r

  private def jArrayClass(elemClazz: Class[_]): Class[_] = {
    jl.reflect.Array.newInstance(elemClazz, 0).getClass
  }

  def erasureClass(tpe: Type) = try typeToJavaClass(tpe.erasure) catch {
    case _: ClassNotFoundException | _: NoClassDefFoundError => null
  }

  /** The Java class that corresponds to given Scala type.
   * Pre: Scala type is already transformed to Java level.
   */
  def typeToJavaClass(tpe: Type): Class[_] = tpe match {
    case ExistentialType(_, rtpe) => typeToJavaClass(rtpe)
    case TypeRef(_, ArrayClass, List(elemtpe)) => jArrayClass(typeToJavaClass(elemtpe))
    case TypeRef(_, sym: ClassSymbol, _) => classToJava(sym.asClass)
    case tpe@TypeRef(_, sym: AliasTypeSymbol, _) => typeToJavaClass(tpe.dealias)
    case SingleType(_, sym: ModuleSymbol) => classToJava(sym.moduleClass.asClass)
    case _ => throw new NoClassDefFoundError("no Java class corresponding to " + tpe + " found")
  }

  /** The Java class corresponding to given Scala class.
   * Note: This only works for
   * - top-level classes
   * - Scala classes that were generated via jclassToScala
   * - classes that have a class owner that has a corresponding Java class
   *
   * @throws A `ClassNotFoundException` for all Scala classes not in one of these categories.
   */
  @throws(classOf[ClassNotFoundException])
  private def classToJava(clazz: ClassSymbol): Class[_] = {
    def noClass = throw new ClassNotFoundException("no Java class corresponding to " + clazz + " found")
    //println("classToJava "+clazz+" "+clazz.owner+" "+clazz.owner.isPackageClass)//debug
    if (clazz.isPrimitiveValueClass)
      valueClassToJavaType(clazz)
    else if (clazz == ArrayClass)
      noClass
    else if (clazz.owner.isPackageClass)
      javaClass(clazz.javaClassName)
    else if (clazz.owner.isClass) {
      val childOfClass = !clazz.owner.isModuleClass
      val childOfTopLevel = clazz.owner.owner.isPackageClass
      val childOfTopLevelObject = clazz.owner.isModuleClass && childOfTopLevel

      // suggested in https://issues.scala-lang.org/browse/SI-4023?focusedCommentId=54759#comment-54759
      var ownerClazz = classToJava(clazz.owner.asClass)
      if (childOfTopLevelObject) ownerClazz = Class.forName(ownerClazz.getName stripSuffix "$", true, ownerClazz.getClassLoader)
      val ownerChildren = ownerClazz.getDeclaredClasses

      var fullNameOfJavaClass = ownerClazz.getName
      if (childOfClass || childOfTopLevel) fullNameOfJavaClass += "$"
      fullNameOfJavaClass += clazz.name

      // compactify (see SI-7779)
      fullNameOfJavaClass = fullNameOfJavaClass match {
        case PackageAndClassPattern(pack, clazzName) =>
          // in a package
          pack + compactifier(clazzName)
        case _ =>
          // in the empty package
          compactifier(fullNameOfJavaClass)
      }

      if (clazz.isModuleClass) fullNameOfJavaClass += "$"

      ownerChildren.find(_.getName == fullNameOfJavaClass).getOrElse(noClass)
    } else
      noClass
  }

  private def javaClass(path: String): Class[_] =
    Class.forName(path, false, classLoader)

  private final object compactifier extends (String => String) {
    val md5 = MessageDigest.getInstance("MD5")

    /**
     * COMPACTIFY
     *
     * The maximum length of a filename on some platforms is 240 chars (docker).
     * Therefore, compactify names that would create a filename longer than that.
     * A compactified name looks like
     * prefix + $$$$ + md5 + $$$$ + suffix,
     * where the prefix and suffix are the first and last quarter of the name,
     * respectively.
     *
     * So how long is too long? For a (flattened class) name, the resulting file
     * will be called "name.class", or, if it's a module class, "name$.class"
     * (see scala/bug#8199). Therefore the maximum suffix is 7 characters, and
     * names that are over (240 - 7) characters get compactified.
     */
    final val marker = "$$$$"
    final val MaxSuffixLength = 7 // "$.class".length + 1 // potential module class suffix and file extension
    final val MaxNameLength = 240 - MaxSuffixLength

    def toMD5(s: String, edge: Int): String = {
      val prefix = s take edge
      val suffix = s takeRight edge

      val cs = s.toArray
      val bytes = Codec.toUTF8(new scala.runtime.ArrayCharSequence(cs, 0, cs.length))
      md5 update bytes
      val md5chars = (md5.digest() map (b => (b & 0xFF).toHexString)).mkString

      prefix + marker + md5chars + marker + suffix
    }
    def apply(s: String): String = (
      if (s.length <= MaxNameLength) s
      else toMD5(s, MaxNameLength / 4)
      )
  }

  private def expandedName(sym: Symbol): String =
    if (sym.isPrivate) nme.expandedName(sym.name.toTermName, sym.owner).toString
    else sym.name.toString

  /** The Java field corresponding to a given Scala field.
   *
   * @param   fld The Scala field.
   */
  def fieldToJava(fld: TermSymbol): Field = {
    val jclazz = classToJava(fld.owner.asClass)
    val jname = fld.name.dropLocal.toString
    try jclazz getDeclaredField jname
    catch {
      case ex: NoSuchFieldException => jclazz getDeclaredField expandedName(fld)
    }
  }

  /** The Java method corresponding to a given Scala method.
   *
   * @param   meth The Scala method
   */
  def methodToJava(meth: MethodSymbol): Method = {
    val jclazz = classToJava(meth.owner.asClass)
    val paramClasses = transformedType(meth).paramTypes map typeToJavaClass
    val jname = meth.name.dropLocal.toString
    try jclazz getDeclaredMethod(jname, paramClasses: _*)
    catch {
      case ex: NoSuchMethodException =>
        jclazz getDeclaredMethod(expandedName(meth), paramClasses: _*)
    }
  }

  /** The Java constructor corresponding to a given Scala constructor.
   *
   * @param   constr The Scala constructor
   */
  def constructorToJava(constr: MethodSymbol): Constructor[_] = {
    val jclazz = classToJava(constr.owner.asClass)
    val paramClasses = transformedType(constr).paramTypes map typeToJavaClass
    val effectiveParamClasses =
      if (!constr.owner.owner.isStaticOwner) jclazz.getEnclosingClass +: paramClasses
      else paramClasses
    jclazz getDeclaredConstructor (effectiveParamClasses: _*)
  }

  def memberToJava(symbol: Symbol): Option[Member] = try symbol match {
    case ms: MethodSymbol if ms.isConstructor => Some(constructorToJava(ms))
    case ms: MethodSymbol => Some(methodToJava(ms))
    case ts: TermSymbol if ts.isJava => Some(fieldToJava(ts))
    case _ => None
  } catch {
    case _: ReflectiveOperationException => None
  }

}
