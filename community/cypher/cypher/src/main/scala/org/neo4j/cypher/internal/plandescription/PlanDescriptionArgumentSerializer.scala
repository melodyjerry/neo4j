/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.plandescription

import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.ir.ordering.ProvidedOrder
import org.neo4j.cypher.internal.util.UnNamedNameGenerator.NameString
import org.neo4j.cypher.internal.plandescription.Arguments.ByteCode
import org.neo4j.cypher.internal.plandescription.Arguments.ConstraintName
import org.neo4j.cypher.internal.plandescription.Arguments.CountNodesExpression
import org.neo4j.cypher.internal.plandescription.Arguments.CountRelationshipsExpression
import org.neo4j.cypher.internal.plandescription.Arguments.Database
import org.neo4j.cypher.internal.plandescription.Arguments.DatabaseAction
import org.neo4j.cypher.internal.plandescription.Arguments.DbHits
import org.neo4j.cypher.internal.plandescription.Arguments.DbmsAction
import org.neo4j.cypher.internal.plandescription.Arguments.EntityByIdRhs
import org.neo4j.cypher.internal.plandescription.Arguments.EstimatedRows
import org.neo4j.cypher.internal.plandescription.Arguments.ExpandExpression
import org.neo4j.cypher.internal.plandescription.Arguments.Expression
import org.neo4j.cypher.internal.plandescription.Arguments.Expressions
import org.neo4j.cypher.internal.plandescription.Arguments.GlobalMemory
import org.neo4j.cypher.internal.plandescription.Arguments.Index
import org.neo4j.cypher.internal.plandescription.Arguments.IndexName
import org.neo4j.cypher.internal.plandescription.Arguments.KeyExpressions
import org.neo4j.cypher.internal.plandescription.Arguments.KeyNames
import org.neo4j.cypher.internal.plandescription.Arguments.LabelName
import org.neo4j.cypher.internal.plandescription.Arguments.Memory
import org.neo4j.cypher.internal.plandescription.Arguments.MergePattern
import org.neo4j.cypher.internal.plandescription.Arguments.Order
import org.neo4j.cypher.internal.plandescription.Arguments.PageCacheHitRatio
import org.neo4j.cypher.internal.plandescription.Arguments.PageCacheHits
import org.neo4j.cypher.internal.plandescription.Arguments.PageCacheMisses
import org.neo4j.cypher.internal.plandescription.Arguments.PipelineInfo
import org.neo4j.cypher.internal.plandescription.Arguments.Planner
import org.neo4j.cypher.internal.plandescription.Arguments.PlannerImpl
import org.neo4j.cypher.internal.plandescription.Arguments.PlannerVersion
import org.neo4j.cypher.internal.plandescription.Arguments.Qualifier
import org.neo4j.cypher.internal.plandescription.Arguments.Resource
import org.neo4j.cypher.internal.plandescription.Arguments.Role
import org.neo4j.cypher.internal.plandescription.Arguments.Rows
import org.neo4j.cypher.internal.plandescription.Arguments.Runtime
import org.neo4j.cypher.internal.plandescription.Arguments.RuntimeImpl
import org.neo4j.cypher.internal.plandescription.Arguments.RuntimeVersion
import org.neo4j.cypher.internal.plandescription.Arguments.Scope
import org.neo4j.cypher.internal.plandescription.Arguments.Signature
import org.neo4j.cypher.internal.plandescription.Arguments.SourceCode
import org.neo4j.cypher.internal.plandescription.Arguments.Time
import org.neo4j.cypher.internal.plandescription.Arguments.UpdateActionName
import org.neo4j.cypher.internal.plandescription.Arguments.User
import org.neo4j.cypher.internal.plandescription.Arguments.Version
import org.neo4j.cypher.internal.util.UnNamedNameGenerator.NameString

object PlanDescriptionArgumentSerializer {
  private val SEPARATOR = ", "
  private val UNNAMED_PATTERN = """  (UNNAMED|FRESHID|AGGREGATION)(\d+)""".r
  private val DEDUP_PATTERN =   """  ([^\s]+)@\d+""".r
  private val stringifier = ExpressionStringifier(e => e.asCanonicalStringVal)
  def asPrettyString(e: expressions.Expression): String =
    if (e == null)
      "null"
    else
      removeGeneratedNames(stringifier(e))

  def serialize(arg: Argument): AnyRef = {

    arg match {
      case Expression(expr) => asPrettyString(expr)
      case Expressions(expressions) => expressions.map {
        case (k, v) => s"$k : ${asPrettyString(v)}"
      }.mkString("{", ", ", "}")
      case UpdateActionName(action) => action
      case MergePattern(startPoint) => s"MergePattern($startPoint)"
      case Index(info: String) => info
      case IndexName(index) => index
      case ConstraintName(constraint) => constraint
      case LabelName(label) => s":$label"
      case KeyNames(keys) => keys.map(removeGeneratedNames).mkString(SEPARATOR)
      case KeyExpressions(expressions) => expressions.mkString(SEPARATOR)
      case DbHits(value) => Long.box(value)
      case Memory(value) => Long.box(value)
      case GlobalMemory(value) => Long.box(value)
      case PageCacheHits(value) => Long.box(value)
      case PageCacheMisses(value) => Long.box(value)
      case PageCacheHitRatio(value) => Double.box(value)
      case _: EntityByIdRhs => arg.toString
      case Rows(value) => Long.box(value)
      case Time(value) => Long.box(value)
      case EstimatedRows(value) => Double.box(value)
      case Order(providedOrder) => serializeProvidedOrder(providedOrder)
      case Version(version) => version
      case Planner(planner) => planner
      case PlannerImpl(plannerName) => plannerName
      case PlannerVersion(value) => value
      case Runtime(runtime) => runtime
      case RuntimeVersion(value) => value
      case DbmsAction(action) => action
      case DatabaseAction(action) => action
      case Database(name) => name
      case Role(name) => name
      case User(name) => name
      case Qualifier(name) => name
      case Resource(name) => name
      case Scope(name) => name
      case SourceCode(className, sourceCode) => sourceCode
      case ByteCode(className, byteCode) => byteCode
      case RuntimeImpl(runtimeName) => runtimeName
      case ExpandExpression(from, rel, typeNames, to, dir: SemanticDirection, min, max) =>
        val left = if (dir == SemanticDirection.INCOMING) "<-" else "-"
        val right = if (dir == SemanticDirection.OUTGOING) "->" else "-"
        val types = typeNames.mkString(":", "|", "")
        val lengthDescr = (min, max) match {
          case (1, Some(1)) => ""
          case (1, None) => "*"
          case (1, Some(m)) => s"*..$m"
          case _ => s"*$min..${max.getOrElse("")}"
        }
        val relInfo = if (lengthDescr == "" && typeNames.isEmpty && rel.unnamed) "" else s"[$rel$types$lengthDescr]"
        s"($from)$left$relInfo$right($to)"
      case CountNodesExpression(ident, label) =>
        val node = label.map(":" + _).mkString
        s"count( ($node) )" + (if (ident.startsWith(" ")) "" else s" AS $ident")
      case CountRelationshipsExpression(ident, startLabel, typeNames, endLabel) =>
        val start = startLabel.map(l => ":" + l).mkString
        val end = endLabel.map(l => ":" + l).mkString
        val types = typeNames.mkString(":", "|", "")
        s"count( ($start)-[$types]->($end) )" + (if (ident.unnamed) "" else s" AS $ident")
      case Signature(procedureName, args, results) =>
        val argString = args.mkString(", ")
        val resultString = results.map { case (name, typ) => s"$name :: $typ" }.mkString(", ")
        s"$procedureName($argString) :: ($resultString)"
      case PipelineInfo(pipelineId, fused) =>
        val fusion = if (fused) "Fused in" else "In"
        s"$fusion Pipeline $pipelineId"

      // Do not add a fallthrough here - we rely on exhaustive checking to ensure
      // that we don't forget to add new types of arguments here
    }
  }

  def serializeProvidedOrder(providedOrder: ProvidedOrder): String = {
    providedOrder.columns.map(col => {
      val direction = if (col.isAscending) "ASC" else "DESC"
      s"${removeGeneratedNames(col.expression.asCanonicalStringVal)} $direction"
    }).mkString(", ")
  }

  def removeGeneratedNames(s: String): String = {
    val named = UNNAMED_PATTERN.replaceAllIn(s, m => s"anon[${m group 2}]")
    deduplicateVariableNames(named)
  }

  def deduplicateVariableNames(in: String): String = {
    val sb = new StringBuilder
    var i = 0
    for (m <- DEDUP_PATTERN.findAllMatchIn(in)) {
      sb ++= in.substring(i, m.start)
      sb ++= m.group(1)
      i = m.end
    }
    sb ++= in.substring(i)
    sb.toString()
  }
}
