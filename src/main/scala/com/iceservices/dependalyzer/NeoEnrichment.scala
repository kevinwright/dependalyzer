package com.iceservices.dependalyzer

import org.neo4j.graphdb.{
  Entity,
  GraphDatabaseService,
  Label,
  Node,
  RelationshipType,
  ResourceIterator,
  Transaction
}

import scala.jdk.CollectionConverters.*
import zio.{ZIO, *}

import java.io.IOException

object NeoEnrichment:
  given Conversion[String, Label] = (str: String) => Label.label(str)

  extension (entity: Entity) def elementId: ElementId = ElementId(entity.getElementId)

  extension (tx: Transaction)

    def autoCommit[A](fn: Transaction => Task[A]): Task[A] =
      fn(tx).tap(_ => ZIO.attemptBlockingIO(tx.commit()))

    def withIterator[A, I](
      make: Transaction => ResourceIterator[I]
    )(
      use: Iterator[I] => Task[A]
    ): Task[A] =
      for {
        iter <- ZIO.attemptBlockingIO(make(tx))
        result <- use(iter.asScala)
        _ <- ZIO.attempt(iter.close())
      } yield result

    def find(stub: NodeStub): Task[Option[Node]] =
      withIterator(_.findNodes(stub.label, stub.javaProps))(iter => ZIO.succeed(iter.nextOption()))

    def insert(stub: NodeStub): Task[Node] = ZIO.attempt {
      val node = tx.createNode(stub.label)
      stub.props.foreach((k, v) => node.setProperty(k, v))
      node
    }

    def upsert(stub: NodeStub): Task[Node] =
      find(stub).flatMap(_.fold(insert(stub))(ZIO.succeed))

    def nodeByElementId(id: ElementId): Task[Node] =
      ZIO.attempt(tx.getNodeByElementId(id.toString))

  extension (gdb: GraphDatabaseService)

    private def withTxManualCommit[A](fn: Transaction => Task[A]): Task[A] =
      ZIO.scoped {
        ZIO.fromAutoCloseable(ZIO.attempt(gdb.beginTx())).flatMap(fn)
      }

    private def withTxAutoCommit[A](fn: Transaction => Task[A]): Task[A] =
      withTxManualCommit(_.autoCommit(fn))

    def allByLabel(label: String): Task[Set[Node]] =
      withTxAutoCommit(
        _.withIterator(_.findNodes(label)) { iter =>
          ZIO.succeed(iter.toSet)
        }
      )

    def idOf(stub: NodeStub): Task[Option[ElementId]] =
      withTxAutoCommit {
        _.find(stub).map(_.map(_.elementId))
      }

    def simpleUpsert(stub: NodeStub): Task[ElementId] =
      withTxAutoCommit { _.upsert(stub).map(_.elementId) }

    def bulkUpsertRelationships(
      pairs: Seq[(NodeStub, NodeStub)],
      relType: RelationshipType
    ): Task[Seq[ElementId]] =
      withTxAutoCommit { tx =>
        ZIO.foreach(pairs) { (fromStub, toStub) =>
          for {
            fromNode <- tx.upsert(fromStub)
            toNode <- tx.upsert(toStub)
            rel = fromNode.createRelationshipTo(toNode, relType)
          } yield rel.elementId
        }
      }

    def upsertRelationship(
      fromStub: NodeStub,
      toStub: NodeStub,
      relType: RelationshipType
    ): Task[ElementId] =
      withTxAutoCommit { tx =>
        for {
          fromNode <- tx.upsert(fromStub)
          toNode <- tx.upsert(toStub)
          rel = fromNode.createRelationshipTo(toNode, relType)
        } yield rel.elementId
      }

    def upsertRelationshipById(
      fromId: ElementId,
      toId: ElementId,
      relType: RelationshipType
    ): Task[ElementId] =
      withTxAutoCommit { tx =>
        for {
          fromNode <- tx.nodeByElementId(fromId)
          toNode <- tx.nodeByElementId(toId)
          rel = fromNode.createRelationshipTo(toNode, relType)
        } yield rel.elementId
      }

    def bulkUpsert(
      stubs: Seq[NodeStub]
    ): Task[Seq[ElementId]] =
      withTxAutoCommit { tx =>
        ZIO.foreach(stubs) { stub => tx.upsert(stub).map(_.elementId) }
      }
