package com.swissborg.sbr.protobuf

import akka.actor.{ActorPath, ExtendedActorSystem}
import akka.cluster.UniqueAddress
import akka.serialization.{SerializationExtension, Serializer, SerializerWithStringManifest, Serializers}
import com.google.protobuf.ByteString
import com.swissborg.sbr.failuredetector.SBFailureDetector.{Contention, ContentionAck}
import com.swissborg.sbr.failuredetector.{SBFailureDetectorProtocol => fd}

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
    fromProto(fd.SBFailureDetectorMsg.parseFrom(bytes))

  private def fromProto(msg: fd.SBFailureDetectorMsg): AnyRef = msg match {
    case fd.SBFailureDetectorMsg(fd.SBFailureDetectorMsg.Payload.Contention(contention)) =>
      contentionFromProto(contention)

    case fd.SBFailureDetectorMsg(fd.SBFailureDetectorMsg.Payload.Ack(ack)) =>
      contentionAckFromProto(ack)

    case other => throw new SerializationException(s"Cannot decode $other")
  }

  private def contentionToProto(contention: Contention): fd.Contention = contention match {
    case Contention(protester, observer, subject, version) =>
      fd.Contention()
        .withProtester(toAkkaInternalProto(protester))
        .withObserver(toAkkaInternalProto(observer))
        .withSubject(toAkkaInternalProto(subject))
        .withVersion(version)
  }

  private def contentionToProtoByteArray(contention: Contention): Array[Byte] =
    fd.SBFailureDetectorMsg().withContention(contentionToProto(contention)).toByteArray

  private def contentionFromProto(contention: fd.Contention): Contention = contention match {
    case fd.Contention(Some(protester), Some(observer), Some(subject), Some(version)) =>
      Contention(
        toUniqueAddress(protester),
        toUniqueAddress(observer),
        toUniqueAddress(subject),
        version
      )

    case _ => throw new SerializationException(s"Missing fields in $contention")
  }

  private def contentionAckToProto(contentionAck: ContentionAck): fd.ContentionAck = contentionAck match {
    case ContentionAck(from, observer, subject, version) =>
      fd.ContentionAck()
        .withFrom(toAkkaInternalProto(from))
        .withObserver(toAkkaInternalProto(observer))
        .withSubject(toAkkaInternalProto(subject))
        .withVersion(version)
  }

  private def contentionAckToProtoByteArray(contentionAck: ContentionAck): Array[Byte] =
    fd.SBFailureDetectorMsg().withAck(contentionAckToProto(contentionAck)).toByteArray

  private def contentionAckFromProto(contentionAck: fd.ContentionAck): ContentionAck = contentionAck match {
    case fd.ContentionAck(Some(to), Some(observer), Some(subject), Some(version)) =>
      ContentionAck(toActorPath(to), toUniqueAddress(observer), toUniqueAddress(subject), version)

    case _ => throw new SerializationException(s"Missing fields in $contentionAck")
  }

  private def toAkkaInternalProto[A <: AnyRef](a: A): fd.AkkaInternal = {
    val serializer = SerializationExtension(system).serializerFor(a.getClass)

    val res0 = fd.AkkaInternal().withSerializerId(serializer.identifier)

    val res1 = if (serializer.includeManifest) {
      val manifest = Serializers.manifestFor(serializer, a)
      res0.withManifest(manifest)
    } else {
      res0
    }

    res1.withBytes(ByteString.copyFrom(serializer.toBinary(a)))
  }

  private def toUniqueAddress(uniqueAddress: fd.AkkaInternal): UniqueAddress =
    fromAkkaInternalProtoByteArray[UniqueAddress](uniqueAddress.serializerId,
                                                  uniqueAddress.manifest,
                                                  uniqueAddress.bytes.toByteArray)

  private def toActorPath(actorPath: fd.AkkaInternal): ActorPath =
    fromAkkaInternalProtoByteArray[ActorPath](actorPath.serializerId, actorPath.manifest, actorPath.bytes.toByteArray)

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
