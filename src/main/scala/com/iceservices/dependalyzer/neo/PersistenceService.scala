package com.iceservices.dependalyzer.neo

import com.iceservices.dependalyzer.neo.PersistenceService
import com.iceservices.dependalyzer.models.*
import com.iceservices.dependalyzer.neo.NeoCodec
import com.iceservices.dependalyzer.neo.NeoEnrichment.*
import org.neo4j.graphdb.{GraphDatabaseService, Node, RelationshipType}
import zio.Console.*
import zio.{Task, ZIO, ZLayer}

class PersistenceService(neo: GraphDatabaseService):
  def bulkUpsert[P <: Persistable](xs: Seq[P]): Task[Seq[Persisted[P]]] =
    neo
      .bulkUpsert(xs.map(_.toStub))
      .map(nodes => xs.zip(nodes.map(_.elementId)).map(Persisted.apply))

  def bulkUpsertDependencies(
    deps: Seq[DependsAdjacency],
  ): Task[Seq[RelationshipStub]] =
    neo
      .bulkUpsertRelationships(deps.map(_.toStub))

  def bulkUpsertParentage(
    deps: Seq[ParentAdjacency],
  ): Task[Seq[RelationshipStub]] =
    neo
      .bulkUpsertRelationships(deps.map(_.toStub))

  def getAll[P <: Persistable](using codec: NeoCodec[P]): Task[Set[Persisted[P]]] =
    for (nodes <- neo.allByLabel(codec.label))
      yield nodes.map(codec.fromPersistedNodeStub)

object PersistenceService:
  val live = ZLayer.fromFunction(new PersistenceService(_))
