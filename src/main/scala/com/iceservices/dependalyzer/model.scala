package com.iceservices.dependalyzer

import org.neo4j.graphdb.RelationshipType

enum Rel extends Enum[Rel] with RelationshipType {
  case DEPENDS_ON, CHILD_OF
}

opaque type ElementId = String

object ElementId:
  def apply(s: String): ElementId = s

extension (id: ElementId) def toString: String = id

trait Persistable extends Product

case class NodeStub(
  label: String,
  props: Map[String, String]
) {
  import scala.jdk.CollectionConverters.*

  def javaProps: java.util.Map[String, AnyRef] =
    props.view.mapValues(_.asInstanceOf[AnyRef]).toMap.asJava
}

case class Persisted[T <: Persistable](value: T, id: ElementId) {
  override def toString: String = s"«$id»$value"
  def ===[T](other: T): Boolean = value == other
}

extension [P <: Persistable](p: P)
  inline def ===(other: Persisted[P]): Boolean = other.value == this

case class Organisation(
  name: String
) extends Persistable

case class UnversionedModule(
  orgName: String,
  moduleName: String
) extends Persistable {
  def organisation: Organisation = Organisation(orgName)
  override def toString: String = s"$orgName:$moduleName"
}

case class VersionedModule(
  orgName: String,
  moduleName: String,
  version: String
) extends Persistable {
  def organisation: Organisation = Organisation(orgName)
  override def toString: String = s"$orgName:$moduleName@$version"
}

case class DependsAdjacency(
  from: VersionedModule,
  to: VersionedModule
) {
  override def toString: String = s"$from -[depends on]-> $to"
}

case class ParentAdjacency(
  child: VersionedModule,
  parent: VersionedModule
) {
  override def toString: String = s"$child -[child of]-> $parent"
}

case class DependsOnById(fromId: ElementId, toId: ElementId) extends Persistable

case class ParentageById(childId: ElementId, parentId: ElementId) extends Persistable
