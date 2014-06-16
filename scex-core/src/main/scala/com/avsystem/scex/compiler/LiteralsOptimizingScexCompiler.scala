package com.avsystem.scex
package compiler

import java.util.concurrent.TimeUnit
import java.{lang => jl, util => ju}

import com.avsystem.scex.compiler.LiteralsOptimizingScexCompiler.ConversionSupplier
import com.avsystem.scex.compiler.ScexCompiler.{CompilationFailedException, CompileError}
import com.avsystem.scex.compiler.presentation.ScexPresentationCompiler
import com.avsystem.scex.util.Literal
import com.google.common.cache.CacheBuilder

import scala.reflect.internal.util.BatchSourceFile
import scala.util.{Success, Try}

/**
 * Avoids actual compilation of most simple template literal expressions by trying to parse them
 * immediately into resulting values. This also means that conversion of literal values to expected result type
 * is performed immediately during compilation and conversion errors will be reported as compilation errors.
 *
 * Created: 01-04-2014
 * Author: ghik
 */
trait LiteralsOptimizingScexCompiler extends ScexPresentationCompiler {

  import com.avsystem.scex.util.CacheImplicits._

  private val literalConversionsCache = CacheBuilder.newBuilder
    .expireAfterAccess(config.expressionExpirationTime, TimeUnit.MILLISECONDS)
    .build[(ExpressionProfile, String, String), Try[Literal => Any]]((compileLiteralConversion _).tupled)

  private def getLiteralConversion(exprDef: ExpressionDef) =
    literalConversionsCache.get((exprDef.profile, exprDef.resultType, exprDef.header))

  private case class LiteralExpression(value: Any)(val debugInfo: ExpressionDebugInfo) extends RawExpression {
    def apply(ctx: ExpressionContext[_, _]) = value
  }

  val interpolatedParamStart = "(^|[^$])(\\$\\$)*\\$([^$]|$)".r

  /**
   * Compiles a dummy expression that tests if there is a valid implicit conversion from Literal to expected type
   * that is not a macro and does not reference context or root object (and thus is independent of expression input).
   * If there is no such conversion, <code>LiteralsOptimizingScexCompiler</code> will not attempt to optimize the
   * compilation and simply pass it to <code>super.compileExpression</code>.
   */
  private def validateLiteralConversion(exprDef: ExpressionDef) = {
    import com.avsystem.scex.compiler.CodeGeneration._
    val actualHeader = implicitLiteralViewHeader(exprDef.header)
    val validationExpression = implicitLiteralViewExpression(exprDef.resultType)
    val validationExprDef = exprDef.copy(template = false, setter = false, expression = validationExpression,
      positionMapping = PositionMapping.empty, header = actualHeader)
    super.compileExpression(validationExprDef)
  }

  private def compileLiteralConversion(profile: ExpressionProfile, resultType: String, header: String) = underLock {
    import com.avsystem.scex.compiler.CodeGeneration._

    val pkgName = newPackageName("_scex_conversion_supplier")
    val profileObjectPkg = compileProfileObject(profile).get
    val conversionSupplierClass = wrapInSource(implicitLiteralConversionClass(profileObjectPkg,
      profile.expressionHeader, header, resultType), pkgName)
    val classLoader = createDedicatedClassLoader("(scex_conversion_supplier)")

    def result =
      compile(new BatchSourceFile(pkgName, conversionSupplierClass), classLoader, usedInExpressions = false) match {
        case Nil =>
          Class.forName(s"$pkgName.$ConversionSupplierClassName", true, classLoader)
            .newInstance.asInstanceOf[ConversionSupplier[Any]].get
        case errors =>
          throw new CompilationFailedException(conversionSupplierClass, errors)
      }

    Try(result)
  }

  private def toCompileError(expr: String, throwable: Throwable) =
    new CompileError(expr, 1, throwable.getClass.getName + ": " + throwable.getMessage)

  private def isEligible(exprDef: ExpressionDef) =
    exprDef.template && !exprDef.setter &&
      !interpolatedParamStart.findFirstIn(exprDef.expression).isDefined &&
      (isStringSupertype(exprDef.resultType) || validateLiteralConversion(exprDef).isSuccess)

  private def toLiteral(exprDef: ExpressionDef) =
    Literal(preprocess(exprDef).expression.replaceAllLiterally("$$", "$"))

  private def isStringSupertype(tpe: String) =
    JavaTypeParsing.StringSupertypes.contains(tpe)

  override protected def compileExpression(exprDef: ExpressionDef) = {
    val sourceInfo = new SourceInfo(null, exprDef.expression, 0, exprDef.expression.length, 1, exprDef.expression.count(_ == '\n') + 2)
    val debugInfo = new ExpressionDebugInfo(exprDef, sourceInfo)

    if (isEligible(exprDef)) {
      val literal = toLiteral(exprDef)
      if (isStringSupertype(exprDef.resultType))
        Success(LiteralExpression(literal.literalString)(debugInfo))
      else getLiteralConversion(exprDef).map { conversion =>
        try LiteralExpression(conversion(literal))(debugInfo) catch {
          case throwable: Throwable =>
            throw new CompilationFailedException(literal.literalString,
              List(toCompileError(literal.literalString, throwable)))
        }
      }
    } else super.compileExpression(exprDef)
  }

  override protected def getErrors(exprDef: ExpressionDef) = super.getErrors(exprDef) match {
    case Nil if isEligible(exprDef) && !isStringSupertype(exprDef.resultType) =>
      getLiteralConversion(exprDef).map { conversion =>
        val literal = toLiteral(exprDef)
        try {
          conversion(literal)
          Nil
        } catch {
          case throwable: Throwable =>
            List(toCompileError(literal.literalString, throwable))
        }
      }.getOrElse(Nil)

    case errors => errors
  }

  override def reset() = underLock {
    super.reset()
    literalConversionsCache.invalidateAll()
  }
}

object LiteralsOptimizingScexCompiler {

  trait ConversionSupplier[+T] {
    def get: Literal => T
  }

  import scala.language.experimental.macros

  def reifyImplicitView[T](arg: Any): T = macro Macros.reifyImplicitView_impl[T]

  def checkConstant[T](expr: T): T = macro Macros.checkConstantExpr_impl[T]
}