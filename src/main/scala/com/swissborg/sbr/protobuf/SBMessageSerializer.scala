package com.swissborg.sbr.protobuf

import akka.actor.ExtendedActorSystem
import akka.cluster.UniqueAddress
import akka.serialization.{
  SerializationExtension,
  Serializer,
  SerializerWithStringManifest,
  Serializers
}
import com.google.protobuf.ByteString
import com.swissborg.sbr.reachability.SBReachabilityReporter.{Contention, ContentionAck}
import com.swissborg.sbr.reachability.{SBReachabilityReporterProtocol => rr}

class SBMessageSerializer(system: ExtendedActorSystem) extends Serializer {
  import SBMessageSerializer._

  override val identifier: Int = 628347598

  override val includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case contention: Contention       => contentionToProtoByteArray(contention)
    case contentionAck: ContentionAck => contentionAckToProtoByteArray(contentionAck)
    case other                        => throw new SerializationException(s"Cannot serialize $other")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    fromProto(rr.SBReachabilityReporterMsg.parseFrom(bytes))

  private def fromProto(msg: rr.SBReachabilityReporterMsg): AnyRef = msg match {
    case rr.SBReachabilityReporterMsg(
        rr.SBReachabilityReporterMsg.Payload.Contention(contention)) =>
      contentionFromProto(contention)

    case rr.SBReachabilityReporterMsg(rr.SBReachabilityReporterMsg.Payload.ContentionAck(ack)) =>
      contentionAckFromProto(ack)

    case other => throw new SerializationException(s"Cannot decode $other")
  }

  private def contentionToProtoByteArray(contention: Contention): Array[Byte] = {
    def contentionToProto(contention: Contention): rr.Contention = contention match {
      case Contention(protester, observer, subject, version) =>
        rr.Contention()
          .withProtester(toAkkaInternalProto(protester))
          .withObserver(toAkkaInternalProto(observer))
          .withSubject(toAkkaInternalProto(subject))
          .withVersion(version)
    }

    rr.SBReachabilityReporterMsg().withContention(contentionToProto(contention)).toByteArray
  }

  private def contentionFromProto(contention: rr.Contention): Contention = contention match {
    case rr.Contention(Some(protester), Some(observer), Some(subject), Some(version)) =>
      Contention(
        toUniqueAddress(protester),
        toUniqueAddress(observer),
        toUniqueAddress(subject),
        version
      )

    case _ => throw new SerializationException(s"Missing fields in $contention")
  }

  private def contentionAckToProtoByteArray(contentionAck: ContentionAck): Array[Byte] = {
    def contentionAckToProto(contentionAck: ContentionAck): rr.ContentionAck = contentionAck match {
      case ContentionAck(from, observer, subject, version) =>
        rr.ContentionAck()
          .withFrom(toAkkaInternalProto(from))
          .withObserver(toAkkaInternalProto(observer))
          .withSubject(toAkkaInternalProto(subject))
          .withVersion(version)
    }

    rr.SBReachabilityReporterMsg()
      .withContentionAck(contentionAckToProto(contentionAck))
      .toByteArray
  }

  private def contentionAckFromProto(contentionAck: rr.ContentionAck): ContentionAck =
    contentionAck match {
      case rr.ContentionAck(Some(to), Some(observer), Some(subject), Some(version)) =>
        ContentionAck(toUniqueAddress(to),
                      toUniqueAddress(observer),
                      toUniqueAddress(subject),
                      version)

      case _ => throw new SerializationException(s"Missing fields in $contentionAck")
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
    fromAkkaInternalProtoByteArray[UniqueAddress](uniqueAddress.serializerId,
                                                  uniqueAddress.manifest,
                                                  uniqueAddress.bytes.toByteArray)

  private def fromAkkaInternalProtoByteArray[A <: AnyRef](serializerId: Int,
                                                          manifest: Option[String],
                                                          bytes: Array[Byte]): A = {
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

object SBMessageSerializer {
  class SerializationException(msg: String) extends RuntimeException(msg)
}
