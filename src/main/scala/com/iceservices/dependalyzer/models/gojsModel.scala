package com.iceservices.dependalyzer
package models

import com.iceservices.dependalyzer.*
import com.iceservices.dependalyzer.neo.{NeoCodec, SubGraph, given}
import zio.json.*

case class GojsNode(
  key: ElementId,
  fullName: String,
  org: String,
  name: String,
  version: String,
  isGroup: Boolean = false,
  group: Option[ElementId],
)

object GojsNode:
  given JsonCodec[GojsNode] = DeriveJsonCodec.gen[GojsNode]

case class GojsLink(
  from: ElementId,
  to: ElementId,
  relType: String,
  scope: String,
)

object GojsLink:
  given JsonCodec[GojsLink] = DeriveJsonCodec.gen[GojsLink]

case class GoJsModel(
  nodes: Seq[GojsNode],
  links: Seq[GojsLink],
)

object GoJsModel:
  given JsonCodec[GoJsModel] = DeriveJsonCodec.gen[GoJsModel]

  def fromSubGraph(sg: SubGraph): GoJsModel = {
    val nodeCodec = summon[NeoCodec[VersionedModule]]
    val modules: Seq[Persisted[VersionedModule]] = sg.nodes.map(nodeCodec.fromPersistedNodeStub)
    val parentage = sg.relationships.collect {
      case RelationshipStub(_, Rel.CHILD_OF, from, to, _) =>
        nodeCodec.fromPersistedNodeStub(from) -> nodeCodec.fromPersistedNodeStub(to)
    }
    val parents: Set[Persisted[VersionedModule]] = parentage.map(_._2).toSet
    val nodes = modules.map(vm =>
      GojsNode(
        key = vm.id,
        fullName = s"${vm.value.orgName}:${vm.value.moduleName}@${vm.value.version}",
        org = vm.value.orgName,
        name = vm.value.moduleName,
        version = vm.value.version,
        isGroup = parents.contains(vm),
        group = parentage.find(_._1 == vm).map(_._2.id),
      ),
    )
    val links = sg.relationships
      .filter(_.relType == Rel.DEPENDS_ON)
      .map(rel =>
        GojsLink(
          from = rel.from.persistedId.get,
          to = rel.to.persistedId.get,
          relType = rel.relType.toString,
          scope = rel.props.getOrElse("scope", "*"),
        ),
      )
    GoJsModel(nodes, links)
  }
