package com.iceservices.dependalyzer

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

trait Persistable extends Product

case class RelationshipStub(
  persistedId: Option[ElementId],
  relType: Rel,
  from: NodeStub,
  to: NodeStub,
)

object RelationshipStub:
  given JsonCodec[RelationshipStub] = DeriveJsonCodec.gen[RelationshipStub]

case class RelationshipIdStub(
  persistedId: Option[ElementId],
  from: ElementId,
  to: ElementId,
)

object RelationshipIdStub:
  given JsonCodec[RelationshipIdStub] = DeriveJsonCodec.gen[RelationshipIdStub]

case class NodeStub(
  label: String,
  props: Map[String, String],
  persistedId: Option[ElementId] = None,
) {
  import scala.jdk.CollectionConverters.*

  def javaProps: java.util.Map[String, AnyRef] =
    props.view.mapValues(_.asInstanceOf[AnyRef]).toMap.asJava
}

object NodeStub:
  given JsonCodec[NodeStub] = DeriveJsonCodec.gen[NodeStub]

case class Persisted[T <: Persistable](value: T, id: ElementId) {
  override def toString: String = s"[<$id>]$value"
  def ===[T](other: T): Boolean = value == other
}

extension [P <: Persistable](p: P)
  inline def ===(other: Persisted[P]): Boolean = other.value == this

case class Organisation(
  name: String,
) extends Persistable

case class UnversionedModule(
  orgName: String,
  moduleName: String,
) extends Persistable {
  def organisation: Organisation = Organisation(orgName)
  override def toString: String = s"$orgName:$moduleName"
}

case class VersionedModule(
  orgName: String,
  moduleName: String,
  version: String,
) extends Persistable {
  def organisation: Organisation = Organisation(orgName)
  override def toString: String = s"$orgName:$moduleName@$version"
}

object VersionedModule {
  val matchingRegex: scala.util.matching.Regex = """([^:]+):([^@]+)@(.+)""".r

  def parse(str: String): Option[VersionedModule] =
    str match {
      case matchingRegex(org, name, version) =>
        Some(VersionedModule(org, name, version))
      case _ => None
    }
}

case class DependsAdjacency(
  from: VersionedModule,
  to: VersionedModule,
) {
  override def toString: String = s"$from -[depends on]-> $to"
}

case class ParentAdjacency(
  child: VersionedModule,
  parent: VersionedModule,
) {
  override def toString: String = s"$child -[child of]-> $parent"
}
