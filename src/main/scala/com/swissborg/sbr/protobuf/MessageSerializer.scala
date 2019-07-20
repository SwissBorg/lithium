package com.swissborg.sbr
package protobuf

import akka.actor.ExtendedActorSystem
import akka.cluster.UniqueAddress
import akka.serialization._
import com.google.protobuf.ByteString
import com.swissborg.sbr.reachability._
import com.swissborg.sbr.reachability.{ReachabilityReporterProtocol => rr}

/**
  * Serializer for [[ReachabilityReporter.SuspiciousDetection]] and [[ReachabilityReporter.SuspiciousDetectionAck]]
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
      case suspiciousDetection: ReachabilityReporter.SuspiciousDetection =>
        suspiciousDetectionToProtoByteArray(suspiciousDetection)

      case suspiciousDetectionAck: ReachabilityReporter.SuspiciousDetectionAck =>
        suspiciousDetectionAckToProtoByteArray(suspiciousDetectionAck)

      case other =>
        throw SerializationException(s"Cannot serialize $other")
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    fromProto(rr.ReachabilityReporterMsg.parseFrom(bytes))

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def fromProto(msg: rr.ReachabilityReporterMsg): AnyRef = msg match {
    case rr
          .ReachabilityReporterMsg(
          rr.ReachabilityReporterMsg.Payload.SuspiciousDetection(suspiciousDetection)
          ) =>
      suspiciousDetectionFromProto(suspiciousDetection)

    case rr.ReachabilityReporterMsg(
        rr.ReachabilityReporterMsg.Payload.SuspiciousDetectionAck(ack)
        ) =>
      suspiciousDetectionAckFromProto(ack)

    case other => throw SerializationException(s"Cannot decode $other")
  }

  private def suspiciousDetectionToProtoByteArray(
      suspiciousDetection: ReachabilityReporter.SuspiciousDetection
  ): Array[Byte] = {
    def suspiciousDetectionToProto(
        suspiciousDetection: ReachabilityReporter.SuspiciousDetection
    ): rr.SuspiciousDetection =
      suspiciousDetection match {
        case ReachabilityReporter.SuspiciousDetection(protester, observer, subject, version) =>
          rr.SuspiciousDetection()
            .withProtester(toAkkaInternalProto(protester))
            .withObserver(toAkkaInternalProto(observer))
            .withSubject(toAkkaInternalProto(subject))
            .withVersion(version)
      }

    rr.ReachabilityReporterMsg()
      .withSuspiciousDetection(suspiciousDetectionToProto(suspiciousDetection))
      .toByteArray
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def suspiciousDetectionFromProto(
      suspiciousDetection: rr.SuspiciousDetection
  ): ReachabilityReporter.SuspiciousDetection =
    suspiciousDetection match {
      case rr.SuspiciousDetection(Some(protester), Some(observer), Some(subject), Some(version)) =>
        ReachabilityReporter.SuspiciousDetection(
          toUniqueAddress(protester),
          toUniqueAddress(observer),
          toUniqueAddress(subject),
          version
        )

      case _ => throw SerializationException(s"Missing fields in $suspiciousDetection")
    }

  private def suspiciousDetectionAckToProtoByteArray(
      ack: ReachabilityReporter.SuspiciousDetectionAck
  ): Array[Byte] = {
    def suspiciousDetectionAckToProto(
        ack: ReachabilityReporter.SuspiciousDetectionAck
    ): rr.SuspiciousDetectionAck =
      ack match {
        case ReachabilityReporter.SuspiciousDetectionAck(from, observer, subject, version) =>
          rr.SuspiciousDetectionAck()
            .withFrom(toAkkaInternalProto(from))
            .withObserver(toAkkaInternalProto(observer))
            .withSubject(toAkkaInternalProto(subject))
            .withVersion(version)
      }

    rr.ReachabilityReporterMsg()
      .withSuspiciousDetectionAck(suspiciousDetectionAckToProto(ack))
      .toByteArray
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def suspiciousDetectionAckFromProto(
      suspiciousDetectionAck: rr.SuspiciousDetectionAck
  ): ReachabilityReporter.SuspiciousDetectionAck =
    suspiciousDetectionAck match {
      case rr.SuspiciousDetectionAck(Some(to), Some(observer), Some(subject), Some(version)) =>
        ReachabilityReporter.SuspiciousDetectionAck(
          toUniqueAddress(to),
          toUniqueAddress(observer),
          toUniqueAddress(subject),
          version
        )

      case _ => throw SerializationException(s"Missing fields in $suspiciousDetectionAck")
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
