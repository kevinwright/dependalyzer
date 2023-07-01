package com.iceservices.dependalyzer.neo

import com.iceservices.dependalyzer.models.{NodeStub, Persistable, Persisted}
import org.neo4j.graphdb.*

import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.Mirror

inline def getElemLabels[A <: Tuple]: List[String] = inline erasedValue[A] match {
  case _: EmptyTuple => Nil // stop condition - the tuple is empty
  case _: (head *:
        tail) => // yes, in scala 3 we can match on tuples head and tail to deconstruct them step by step
    val headElementLabel = constValue[head].toString // bring the head label to value space
    val tailElementLabels = getElemLabels[tail] // recursive call to get the labels from the tail
    headElementLabel :: tailElementLabels // concat head + tail
}

inline given deriveNeoCodec[P <: Persistable](using
  m: Mirror.ProductOf[P],
): NeoCodec[P] = {
  val elemLabels = getElemLabels[m.MirroredElemLabels]
  new NeoCodec[P] {
    override val label: String = constValue[m.MirroredLabel]

//    override def toMap(p: P): Map[String, String] =
//      Map(
//        elemLabels.zip(p.productIterator.map(_.toString))*,
//      )

    override def fromPersistedNodeStub(node: NodeStub): Persisted[P] =
      Persisted(
        m.fromProduct(Tuple.fromArray(elemLabels.toArray.map(node.props(_)))),
        node.persistedId.get,
      )

  }
}

trait NeoCodec[P <: Persistable]:
  def label: String
//  def toMap(p: P): Map[String, String]
//  def toStub(p: P): NodeStub = NodeStub(label, toMap(p))
  def fromPersistedNodeStub(node: NodeStub): Persisted[P]
