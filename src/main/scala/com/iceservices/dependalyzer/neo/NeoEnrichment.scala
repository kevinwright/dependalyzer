package com.iceservices.dependalyzer
package neo

import com.iceservices.dependalyzer.models.*
import org.neo4j.graphdb.*
import org.neo4j.graphdb.traversal.{Evaluators, TraversalDescription}
import zio.*

import java.io.IOException
import scala.jdk.CollectionConverters.*

case class SubGraph(nodes: Seq[NodeStub], relationships: Seq[RelationshipStub])

object SubGraph:
  import NeoEnrichment.toStub
  def fromPaths(paths: Iterable[Path]): SubGraph = {
    val (nodeSet, relSet) =
      paths.foldLeft((Set.empty[NodeStub], Set.empty[RelationshipStub])) {
        case ((nodeAcc, relAcc), path) =>
          val newNodeAcc = nodeAcc + path.startNode.toStub + path.endNode.toStub
          val newRelAcc = relAcc ++ path.relationships.asScala.map(_.toStub)
          newNodeAcc -> newRelAcc
      }
    SubGraph(nodeSet.toSeq, relSet.toSeq)
  }

object NeoEnrichment:
  given Conversion[String, Label] = (str: String) => Label.label(str)

  extension (entity: Entity)
    def elementId: ElementId = ElementId(entity.getElementId)
    def props: Map[String, String] =
      entity.getAllProperties.asScala.view.mapValues(_.toString).toMap

    def update(props: Map[String, String]): Task[Node] = ZIO.attempt(
      props.foreach(entity.setProperty.tupled)
    )

  extension (n: Node)
    def toStub: NodeStub =
      NodeStub(
        persistedId = Some(n.elementId),
        label = n.getLabels.asScala.head.toString,
        keys = Set.empty,
        props = n.props,
      )

  extension (r: Relationship)
    def toStub: RelationshipStub =
      RelationshipStub(
        from = r.getStartNode.toStub,
        to = r.getEndNode.toStub,
        relType = Rel.valueOf(r.getType.name),
        persistedId = Some(r.elementId),
        props = r.props,
      )

  extension (tx: Transaction)

    def autoCommit[A](fn: Transaction => Task[A]): Task[A] =
      fn(tx).tap(_ => ZIO.attemptBlockingIO(tx.commit()))

    def withIterator[A, I](
      make: Transaction => ResourceIterator[I],
    )(
      use: Iterator[I] => Task[A],
    ): Task[A] =
      for {
        iter <- ZIO.attemptBlockingIO(make(tx))
        result <- use(iter.asScala)
        _ <- ZIO.attempt(iter.close())
      } yield result

    def find(stub: NodeStub): Task[Option[Node]] =
      withIterator(_.findNodes(stub.label, stub.keyJavaProps))(iter => ZIO.succeed(iter.nextOption()))

    def insert(stub: NodeStub): Task[Node] = ZIO.attempt {
      val node = tx.createNode(stub.label)
      stub.applyPropsToEntity(node)
      node
    }

    def upsert(stub: NodeStub): Task[Node] =
      for {
        found <- find(stub)
        updated <- found match {
          case Some(node) => node.update(stub.nonKeyProps)
          case None => insert(stub)
        }
      } yield updated


    def nodeByElementId(id: ElementId): Task[Node] =
      ZIO.attempt(tx.getNodeByElementId(id.toString))

  extension (gdb: GraphDatabaseService)

    private def withTxManualCommit[A](fn: Transaction => Task[A]): Task[A] =
      ZIO.scoped {
        ZIO.fromAutoCloseable(ZIO.attempt(gdb.beginTx())).flatMap(fn)
      }

    private def withTxAutoCommit[A](fn: Transaction => Task[A]): Task[A] =
      withTxManualCommit(_.autoCommit(fn))

    def allByLabel(label: String): Task[Set[NodeStub]] =
      withTxAutoCommit(
        _.withIterator(_.findNodes(label)) { iter =>
          ZIO.succeed(iter.map(_.toStub).toSet)
        },
      )

    def idOf(stub: NodeStub): Task[Option[ElementId]] =
      withTxAutoCommit {
        _.find(stub).map(_.map(_.elementId))
      }

    def simpleUpsert(stub: NodeStub): Task[NodeStub] =
      withTxAutoCommit { _.upsert(stub).map(_.toStub) }

    def subGraph(stub: NodeStub): Task[Option[SubGraph]] =
      withTxAutoCommit { tx =>
        val codec = summon[NeoCodec[VersionedModule]]
        val td: TraversalDescription = tx
          .traversalDescription()
          .breadthFirst()
          .relationships(Rel.DEPENDS_ON, Direction.OUTGOING)
          .relationships(Rel.CHILD_OF, Direction.BOTH)
//          .evaluator(Evaluators.all())
        tx.find(stub).map { optNode =>
          optNode.map { node =>
            println(s"node was: $node")
            val traverser = td.traverse(node)
            println(s"traverser was: $traverser")

            SubGraph.fromPaths(traverser.asScala)
          }
        }
      }

    def bulkUpsertRelationships(
      relationships: Seq[RelationshipStub],
    ): Task[Seq[RelationshipStub]] =
      withTxAutoCommit { tx =>
        ZIO.foreach(relationships) { relStub =>
          for {
            fromNode <- tx.upsert(relStub.from)
            toNode <- tx.upsert(relStub.to)
            rel <- ZIO.attempt(fromNode.createRelationshipTo(toNode, relStub.relType))
            _ <- ZIO.attempt(relStub.applyPropsToEntity(rel))
          } yield rel.toStub
        }
      }

    def upsertRelationship(
      stub: RelationshipStub,
    ): Task[RelationshipStub] =
      withTxAutoCommit { tx =>
        for {
          fromNode <- tx.upsert(stub.from)
          toNode <- tx.upsert(stub.to)
          rel <- ZIO.attempt(fromNode.createRelationshipTo(toNode, stub.relType))
          _ <- ZIO.attempt(stub.applyPropsToEntity(rel))
        } yield rel.toStub
      }

    def upsertRelationshipById(
      stub: RelationshipIdStub,
    ): Task[RelationshipStub] =
      withTxAutoCommit { tx =>
        for {
          fromNode <- tx.nodeByElementId(stub.from)
          toNode <- tx.nodeByElementId(stub.to)
          rel <- ZIO.attempt(fromNode.createRelationshipTo(toNode, stub.relType))
          _ <- ZIO.attempt(stub.applyPropsToEntity(rel))
        } yield rel.toStub
      }

    def bulkUpsert(
      stubs: Seq[NodeStub],
    ): Task[Seq[Node]] =
      withTxAutoCommit { tx =>
        ZIO.foreach(stubs) { stub => tx.upsert(stub) }
      }
