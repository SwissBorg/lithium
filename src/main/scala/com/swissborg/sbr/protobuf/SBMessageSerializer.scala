package com.swissborg.sbr.protobuf

import akka.actor.{ActorPath, Address}
import akka.cluster.UniqueAddress
import akka.serialization.SerializerWithStringManifest
import com.swissborg.sbr.failuredetector.SBFailureDetector.{Contention, ContentionAck}
import com.swissborg.sbr.failuredetector.{SBFailureDetectorProtocol => fd}

class SBMessageSerializer extends SerializerWithStringManifest {
  import SBMessageSerializer._

  override val identifier: Int = 628347598

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case contention: Contention       => contentionToProtoByteArray(contention)
    case contentionAck: ContentionAck => contentionAckToProtoByteArray(contentionAck)
    case _                            => throw new IllegalArgumentException(s"$o")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case `ContentionManifest`    => contentionFromProtoByteArray(bytes)
    case `ContentionAckManifest` => contentionAckFromProtoByteArray(bytes)
  }

  override def manifest(o: AnyRef): String = o match {
    case _: Contention    => ContentionManifest
    case _: ContentionAck => ContentionAckManifest
  }
}

object SBMessageSerializer {
  final private val ContentionManifest: String =
    s"com.swissborg.sbr.failuredetector.SBFailureDetector$$Contention"

  final private val ContentionAckManifest: String =
    s"com.swissborg.sbr.failuredetector.SBFailureDetector$$ContentionAck"

  private def contentionToProto(contention: Contention): fd.Contention = contention match {
    case Contention(protester, observer, subject, version) =>
      fd.Contention()
        .withProtester(uniqueAddressToProto(protester))
        .withObserver(uniqueAddressToProto(observer))
        .withSubject(uniqueAddressToProto(subject))
        .withVersion(version)
  }

  private def contentionToProtoByteArray(contention: Contention): Array[Byte] =
    contentionToProto(contention).toByteArray

  private def contentionFromProto(contention: fd.Contention): Contention = contention match {
    case fd.Contention(protester, observer, subject, version) =>
      Contention(uniqueAddressFromProto(protester.get),
                 uniqueAddressFromProto(observer.get),
                 uniqueAddressFromProto(subject.get),
                 version.get)
  }

  private def contentionFromProtoByteArray(bytes: Array[Byte]): Contention =
    contentionFromProto(fd.Contention.parseFrom(bytes))

  private def contentionAckToProto(contentionAck: ContentionAck): fd.ContentionAck = contentionAck match {
    case ContentionAck(from, observer, subject, version) =>
      fd.ContentionAck()
        .withFrom(actorPathToProto(from))
        .withObserver(uniqueAddressToProto(observer))
        .withSubject(uniqueAddressToProto(subject))
        .withVersion(version)
  }

  private def contentionAckToProtoByteArray(contentionAck: ContentionAck): Array[Byte] =
    contentionAckToProto(contentionAck).toByteArray

  private def contentionAckFromProto(contentionAck: fd.ContentionAck): ContentionAck = contentionAck match {
    case fd.ContentionAck(to, observer, subject, version) =>
      ContentionAck(actorPathFromProto(to.get),
                    uniqueAddressFromProto(observer.get),
                    uniqueAddressFromProto(subject.get),
                    version.get)
  }

  private def contentionAckFromProtoByteArray(bytes: Array[Byte]): ContentionAck =
    contentionAckFromProto(fd.ContentionAck.parseFrom(bytes))

  private def actorPathToProto(actorPath: ActorPath): fd.ActorPath =
    fd.ActorPath().withPath(actorPath.toSerializationFormat)

  private def actorPathFromProto(actorPath: fd.ActorPath): ActorPath = actorPath match {
    case fd.ActorPath(path) => ActorPath.fromString(path.get)
  }

  private def uniqueAddressToProto(uniqueAddress: UniqueAddress): fd.UniqueAddress = uniqueAddress match {
    case UniqueAddress(address, longUid) => fd.UniqueAddress().withAddress(addressToProto(address)).withUid(longUid)
  }

  private def uniqueAddressFromProto(uniqueAddress: fd.UniqueAddress): UniqueAddress = uniqueAddress match {
    case fd.UniqueAddress(address, uid) => UniqueAddress(addressFromProto(address.get), uid.get)
  }

  private def addressToProto(address: Address): fd.Address = address match {
    case Address(protocol, system, maybeHost, maybePort) =>
      val r0 = fd.Address().withProtocol(protocol).withSystem(system)
      val r1 = maybeHost.fold(r0)(r0.withHostname)
      maybePort.fold(r1)(r1.withPort)
  }

  private def addressFromProto(address: fd.Address): Address = address match {
    case fd.Address(protocol, system, Some(host), Some(port)) =>
      Address(protocol.get, system.get, host, port)

    case fd.Address(protocol, system, None, None) =>
      Address(protocol.get, system.get)

    case _ => throw new Exception()
  }
}
