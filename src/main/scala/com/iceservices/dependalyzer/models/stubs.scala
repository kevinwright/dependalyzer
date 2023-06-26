package com.iceservices.dependalyzer.models

import org.neo4j.graphdb.Entity
import zio.json.{DeriveJsonCodec, JsonCodec}

trait Stub:
  import scala.jdk.CollectionConverters.*
  def props: Map[String, String]
  def javaProps: java.util.Map[String, AnyRef] =
    props.view.mapValues(_.asInstanceOf[AnyRef]).toMap.asJava
  def applyPropsToEntity(entity: Entity): Unit =
    props.foreach((k, v) => entity.setProperty(k, v))

case class RelationshipStub(
  persistedId: Option[ElementId],
  relType: Rel,
  from: NodeStub,
  to: NodeStub,
  props: Map[String, String],
) extends Stub {
  def toIdStub = RelationshipIdStub(
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
) extends Stub

object RelationshipIdStub:
  given JsonCodec[RelationshipIdStub] = DeriveJsonCodec.gen[RelationshipIdStub]

case class NodeStub(
  label: String,
  props: Map[String, String],
  persistedId: Option[ElementId] = None,
) extends Stub

object NodeStub:
  given JsonCodec[NodeStub] = DeriveJsonCodec.gen[NodeStub]
