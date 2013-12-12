package com.avsystem.scex
package compiler.xmlfriendly

import com.avsystem.scex.ExpressionProfile
import com.avsystem.scex.compiler.ExpressionDef
import java.{util => ju, lang => jl}
import com.avsystem.scex.compiler.presentation.ScexPresentationCompiler

/**
 *
 * Scex compiler that accepts modified, XML-friendly syntax:
 * <ul>
 * <li>string literals can be enclosed in both single and double quotes</li>
 * <li>identifiers 'lt', 'gt', 'lte', 'gte', 'and' and 'or' are
 * aliases of '<', '>', '<=', '>=', '&&' and '||'</li>
 * </ul>
 *
 * Created: 16-08-2013
 * Author: ghik
 */
trait XmlFriendlyScexCompiler extends ScexPresentationCompiler {
  override protected def compileExpression(exprDef: ExpressionDef) = {
    val xmlFriendlyExpression = XmlFriendlyTranslator.translate(exprDef.expression, exprDef.template).result
    super.compileExpression(exprDef.copy(expression = xmlFriendlyExpression))
  }

  override protected def getCompleter(
    profile: ExpressionProfile,
    template: Boolean,
    setter: Boolean,
    header: String,
    contextType: String,
    rootObjectClass: Class[_],
    resultType: String) = {

    val wrapped: Completer =
      super.getCompleter(profile, template, setter, header, contextType, rootObjectClass, resultType)

    new Completer {
      def getErrors(expression: String) =
        wrapped.getErrors(XmlFriendlyTranslator.translate(expression).result)

      def getTypeCompletion(expression: String, position: Int) = {
        val ps = XmlFriendlyTranslator.translate(expression, template)
        wrapped.getTypeCompletion(ps.result, ps.positionMapping(position))
      }

      def getScopeCompletion =
        wrapped.getScopeCompletion
    }
  }

}
