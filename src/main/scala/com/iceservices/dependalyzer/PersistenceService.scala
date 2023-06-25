package com.iceservices.dependalyzer

import org.neo4j.graphdb.{GraphDatabaseService, Node, RelationshipType}
import zio.{Task, ZIO, ZLayer}
import zio.Console.*
import NeoEnrichment.*

class PersistenceService(neo: GraphDatabaseService):
  def bulkUpsert[P <: Persistable](xs: Seq[P])(using codec: NeoCodec[P]): Task[Seq[Persisted[P]]] =
    neo
      .bulkUpsert(xs.map(codec.toStub))
      .map(ids => xs.zip(ids).map(Persisted.apply))

  def bulkUpsertRelationships[P <: Persistable](
    xs: Seq[(P, P)],
    relType: RelationshipType
  )(using codec: NeoCodec[P]): Task[Seq[ElementId]] =
    neo
      .bulkUpsertRelationships(xs.map{(f,t) => (codec.toStub(f), codec.toStub(t))}, relType)

  def getAll[P <: Persistable](using codec: NeoCodec[P]): Task[Set[Persisted[P]]] =
    for (nodes <- neo.allByLabel(codec.label))
      yield nodes.map(codec.fromNode)

object PersistenceService:
  val live = ZLayer.fromFunction(new PersistenceService(_))
