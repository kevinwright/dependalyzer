package com.iceservices.dependalyzer
package models

import com.iceservices.dependalyzer.*
import com.iceservices.dependalyzer.neo.given
import com.iceservices.dependalyzer.neo.NeoCodec
import org.neo4j.graphdb.{Node, RelationshipType}
import zio.json.*

enum Rel extends Enum[Rel] with RelationshipType {
  case DEPENDS_ON, CHILD_OF
}

object Rel:
  given JsonDecoder[Rel] = JsonDecoder.string.map(Rel.valueOf)
  given JsonEncoder[Rel] = JsonEncoder.string.contramap(_.name())

opaque type ElementId = String

object ElementId:
  def apply(s: String): ElementId = s
  given JsonDecoder[ElementId] = JsonDecoder.string.map(ElementId.apply)
  given JsonEncoder[ElementId] = JsonEncoder.string.contramap(_.toString)

extension (id: ElementId) def toString: String = id

trait Persistable extends Product:
  def toStub: NodeStub

case class Persisted[T <: Persistable](value: T, id: ElementId) {
  override def toString: String = s"[<$id>]$value"
  def ===[T](other: T): Boolean = value == other
}

extension [P <: Persistable](p: P)
  inline def ===(other: Persisted[P]): Boolean = other.value == this

case class Organisation(
  name: String,
) extends Persistable:
  def toStub: NodeStub = NodeStub(
    persistedId = None,
    label = "Organisation",
    keys = Set("name"),
    props = Map("name" -> name),
  )

case class UnversionedModule(
  orgName: String,
  moduleName: String,
) extends Persistable:
  def organisation: Organisation = Organisation(orgName)
  override def toString: String = s"$orgName:$moduleName"

  def toStub: NodeStub = NodeStub(
    persistedId = None,
    label = "UnversionedModule",
    keys = Set("orgName", "moduleName"),
    props = Map("orgName" -> orgName, "moduleName" -> moduleName),
  )

case class VersionedModule(
  orgName: String,
  moduleName: String,
  version: String,
) extends Persistable:
  def organisation: Organisation = Organisation(orgName)
  override def toString: String = s"$orgName:$moduleName@$version"

  def toStub: NodeStub = NodeStub(
    persistedId = None,
    label = "VersionedModule",
    keys = Set("orgName", "moduleName", "version"),
    props = Map("orgName" -> orgName, "moduleName" -> moduleName, "version" -> version),
  )

object VersionedModule:
  val matchingRegex: scala.util.matching.Regex = """([^:]+):([^@]+)@(.+)""".r

  def parse(str: String): Option[VersionedModule] =
    str match {
      case matchingRegex(org, name, version) =>
        Some(VersionedModule(org, name, version))
      case _ => None
    }

case class DependsAdjacency(
  from: VersionedModule,
  to: VersionedModule,
  scope: String,
):
  private val codec = summon[NeoCodec[VersionedModule]]
  override def toString: String = s"$from -[depends on ($scope)]-> $to"
  def toStub: RelationshipStub = RelationshipStub(
    persistedId = None,
    relType = Rel.DEPENDS_ON,
    from = from.toStub,
    to = to.toStub,
    props = Map("scope" -> scope),
  )

case class ParentAdjacency(
  child: VersionedModule,
  parent: VersionedModule,
):
  private val codec = summon[NeoCodec[VersionedModule]]
  override def toString: String = s"$child -[child of]-> $parent"

  def toStub: RelationshipStub = RelationshipStub(
    persistedId = None,
    relType = Rel.CHILD_OF,
    from = child.toStub,
    to = parent.toStub,
    props = Map.empty,
  )
