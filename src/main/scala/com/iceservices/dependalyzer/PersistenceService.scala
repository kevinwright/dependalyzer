package com.iceservices.dependalyzer

import org.neo4j.graphdb.{GraphDatabaseService, Node, RelationshipType}
import zio.{Task, ZIO, ZLayer}
import zio.Console.*
import NeoEnrichment.*
import com.iceservices.dependalyzer.models.{DependsAdjacency, ElementId, ParentAdjacency, Persistable, Persisted, RelationshipStub}

class PersistenceService(neo: GraphDatabaseService):
  def bulkUpsert[P <: Persistable](xs: Seq[P])(using codec: NeoCodec[P]): Task[Seq[Persisted[P]]] =
    neo
      .bulkUpsert(xs.map(codec.toStub))
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
