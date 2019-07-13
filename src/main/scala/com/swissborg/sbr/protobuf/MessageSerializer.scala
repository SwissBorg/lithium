package com.swissborg.sbr
package protobuf

import akka.actor.ExtendedActorSystem
import akka.cluster.UniqueAddress
import akka.serialization._
import com.google.protobuf.ByteString
import com.swissborg.sbr.reachability._
import com.swissborg.sbr.reachability.{ReachabilityReporterProtocol => rr}

/**
  * Serializer for [[ReachabilityReporter.Contention]] and [[ReachabilityReporter.ContentionAck]]
  * messages sent over the network between
  * [[com.swissborg.sbr.reachability.ReachabilityReporter]] actors.
  */
class MessageSerializer(system: ExtendedActorSystem) extends Serializer {
  import MessageSerializer._

  override val identifier: Int = 628347598

  override val includeManifest: Boolean = false

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case contention: ReachabilityReporter.Contention =>
        contentionToProtoByteArray(contention)

      case contentionAck: ReachabilityReporter.ContentionAck =>
        contentionAckToProtoByteArray(contentionAck)

      case other =>
        throw SerializationException(s"Cannot serialize $other")
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    fromProto(rr.ReachabilityReporterMsg.parseFrom(bytes))

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def fromProto(msg: rr.ReachabilityReporterMsg): AnyRef = msg match {
    case rr
          .ReachabilityReporterMsg(rr.ReachabilityReporterMsg.Payload.Contention(contention)) =>
      contentionFromProto(contention)

    case rr.ReachabilityReporterMsg(rr.ReachabilityReporterMsg.Payload.ContentionAck(ack)) =>
      contentionAckFromProto(ack)

    case other => throw SerializationException(s"Cannot decode $other")
  }

  private def contentionToProtoByteArray(
      contention: ReachabilityReporter.Contention
  ): Array[Byte] = {
    def contentionToProto(contention: ReachabilityReporter.Contention): rr.Contention =
      contention match {
        case ReachabilityReporter.Contention(protester, observer, subject, version) =>
          rr.Contention()
            .withProtester(toAkkaInternalProto(protester))
            .withObserver(toAkkaInternalProto(observer))
            .withSubject(toAkkaInternalProto(subject))
            .withVersion(version)
      }

    rr.ReachabilityReporterMsg().withContention(contentionToProto(contention)).toByteArray
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def contentionFromProto(contention: rr.Contention): ReachabilityReporter.Contention =
    contention match {
      case rr.Contention(Some(protester), Some(observer), Some(subject), Some(version)) =>
        ReachabilityReporter.Contention(
          toUniqueAddress(protester),
          toUniqueAddress(observer),
          toUniqueAddress(subject),
          version
        )

      case _ => throw SerializationException(s"Missing fields in $contention")
    }

  private def contentionAckToProtoByteArray(
      contentionAck: ReachabilityReporter.ContentionAck
  ): Array[Byte] = {
    def contentionAckToProto(
        contentionAck: ReachabilityReporter.ContentionAck
    ): rr.ContentionAck =
      contentionAck match {
        case ReachabilityReporter.ContentionAck(from, observer, subject, version) =>
          rr.ContentionAck()
            .withFrom(toAkkaInternalProto(from))
            .withObserver(toAkkaInternalProto(observer))
            .withSubject(toAkkaInternalProto(subject))
            .withVersion(version)
      }

    rr.ReachabilityReporterMsg()
      .withContentionAck(contentionAckToProto(contentionAck))
      .toByteArray
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def contentionAckFromProto(
      contentionAck: rr.ContentionAck
  ): ReachabilityReporter.ContentionAck =
    contentionAck match {
      case rr.ContentionAck(Some(to), Some(observer), Some(subject), Some(version)) =>
        ReachabilityReporter.ContentionAck(
          toUniqueAddress(to),
          toUniqueAddress(observer),
          toUniqueAddress(subject),
          version
        )

      case _ => throw SerializationException(s"Missing fields in $contentionAck")
    }

  private def toAkkaInternalProto[A <: AnyRef](a: A): rr.AkkaInternal = {
    val serializer = SerializationExtension(system).serializerFor(a.getClass)

    val res0 = rr.AkkaInternal().withSerializerId(serializer.identifier)

    val res1 = if (serializer.includeManifest) {
      val manifest = Serializers.manifestFor(serializer, a)
      res0.withManifest(manifest)
    } else {
      res0
    }

    res1.withBytes(ByteString.copyFrom(serializer.toBinary(a)))
  }

  private def toUniqueAddress(uniqueAddress: rr.AkkaInternal): UniqueAddress =
    fromAkkaInternalProtoByteArray[UniqueAddress](
      uniqueAddress.serializerId,
      uniqueAddress.manifest,
      uniqueAddress.bytes.toByteArray
    )

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private def fromAkkaInternalProtoByteArray[A <: AnyRef](
      serializerId: Int,
      manifest: Option[String],
      bytes: Array[Byte]
  ): A = {
    val serializer = SerializationExtension(system).serializerByIdentity(serializerId)

    val ref = manifest match {
      case Some(manifest) =>
        serializer match {
          case serializer: SerializerWithStringManifest => serializer.fromBinary(bytes, manifest)
          case _                                        => serializer.fromBinary(bytes, Class.forName(manifest))
        }

      case None =>
        serializer.fromBinary(bytes)
    }

    ref.asInstanceOf[A]
  }
}

object MessageSerializer {
  final case class SerializationException(msg: String) extends RuntimeException(msg)
}
