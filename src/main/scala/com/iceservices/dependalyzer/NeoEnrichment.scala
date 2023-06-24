package com.iceservices.dependalyzer

import org.neo4j.graphdb.{GraphDatabaseService, Label, Node, ResourceIterator, Transaction}

import scala.jdk.CollectionConverters.*
import zio.{ZIO, *}

import java.io.IOException

object NeoEnrichment {
  given Conversion[String, Label] = (str: String) => Label.label(str)

  extension (gdb: GraphDatabaseService)
    private def inTx[A](fn: Transaction => RIO[Scope, A]): Task[A] =
      ZIO.scoped {
        ZIO.fromAutoCloseable(ZIO.attempt(gdb.beginTx())).flatMap(fn)
      }

    private def inTxBlocking[A](fn: Transaction => A): Task[A] =
      inTx(tx => ZIO.attemptBlockingIO(fn(tx)))

    private def iterating[A, I](
      ri: Transaction => ResourceIterator[I]
    )(
      body: ResourceIterator[I] => A
    ): Task[A] =
      inTx(tx => ZIO.fromAutoCloseable(ZIO.attemptBlockingIO(ri(tx))).map(body))

    /////////////////////////
    
    def allByLabel(label: String): Task[Set[Node]] =
      iterating(_.findNodes(label)) { _.asScala.toSet }

    def simpleInsert(
      label: String,
      params: Map[String, String]
    ): Task[String] =
      ZIO.debug("inserting node") *> inTxBlocking { tx =>
        val node = tx.createNode(label)
        params.foreach((k, v) => node.setProperty(k, v))
        tx.commit()
        node.getElementId
      }

    def insertProduct(p: Product): Task[String] =
      simpleInsert(
        p.productPrefix,
        p.productElementNames.zip(p.productIterator.map(_.toString)).toMap
      )

}
