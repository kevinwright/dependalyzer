package com.iceservices.dependalyzer.models

import org.neo4j.graphdb.Entity
import zio.json.{DeriveJsonCodec, JsonCodec}

trait Stub:
  import scala.jdk.CollectionConverters.*
  def keys: Set[String]
  def props: Map[String, String]

  def keyProps: Map[String, String] = props.filter((k, v) => keys.contains(k))
  def nonKeyProps: Map[String, String] = props.filterNot((k, v) => keys.contains(k))

  def javaProps: java.util.Map[String, AnyRef] =
    props.view.mapValues(_.asInstanceOf[AnyRef]).toMap.asJava
  def keyJavaProps: java.util.Map[String, AnyRef] =
    keyProps.view.mapValues(_.asInstanceOf[AnyRef]).toMap.asJava
  def applyPropsToEntity(entity: Entity): Unit =
    props.foreach((k, v) => entity.setProperty(k, v))

case class RelationshipStub(
  persistedId: Option[ElementId],
  relType: Rel,
  from: NodeStub,
  to: NodeStub,
  props: Map[String, String],
) extends Stub {
  val keys: Set[String] = Set("scope")
  def toIdStub: RelationshipIdStub = RelationshipIdStub(
    persistedId,
    relType,
    from.persistedId.get,
    to.persistedId.get,
    props,
  )
}

object RelationshipStub:
  given JsonCodec[RelationshipStub] = DeriveJsonCodec.gen[RelationshipStub]

case class RelationshipIdStub(
  persistedId: Option[ElementId],
  relType: Rel,
  from: ElementId,
  to: ElementId,
  props: Map[String, String],
) extends Stub {
  val keys: Set[String] = Set.empty
}

object RelationshipIdStub:
  given JsonCodec[RelationshipIdStub] = DeriveJsonCodec.gen[RelationshipIdStub]

case class NodeStub(
  label: String,
  keys: Set[String],
  props: Map[String, String],
  persistedId: Option[ElementId] = None,
) extends Stub

object NodeStub:
  given JsonCodec[NodeStub] = DeriveJsonCodec.gen[NodeStub]
