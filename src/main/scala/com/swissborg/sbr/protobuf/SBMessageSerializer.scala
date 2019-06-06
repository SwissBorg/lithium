package com.swissborg.sbr.protobuf

import akka.actor.ExtendedActorSystem
import akka.cluster.UniqueAddress
import akka.serialization.{SerializationExtension, Serializer, SerializerWithStringManifest, Serializers}
import com.google.protobuf.ByteString
import com.swissborg.sbr.reachability.SBReachabilityReporter.{Contention, ContentionAck, Introduction, IntroductionAck}
import com.swissborg.sbr.reachability.SBReachabilityReporterState.ContentionAggregator
import com.swissborg.sbr.reachability.{SBReachabilityReporterProtocol => rr}

class SBMessageSerializer(system: ExtendedActorSystem) extends Serializer {
  import SBMessageSerializer._

  override val identifier: Int = 628347598

  override val includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case contention: Contention           => contentionToProtoByteArray(contention)
    case contentionAck: ContentionAck     => contentionAckToProtoByteArray(contentionAck)
    case introduction: Introduction       => introductionToProtoByteArray(introduction)
    case introductionAck: IntroductionAck => introductionAckToProtoByteArray(introductionAck)
    case other                            => throw new SerializationException(s"Cannot serialize $other")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    fromProto(rr.SBReachabilityReporterMsg.parseFrom(bytes))

  private def fromProto(msg: rr.SBReachabilityReporterMsg): AnyRef = msg match {
    case rr.SBReachabilityReporterMsg(rr.SBReachabilityReporterMsg.Payload.Contention(contention)) =>
      contentionFromProto(contention)

    case rr.SBReachabilityReporterMsg(rr.SBReachabilityReporterMsg.Payload.ContentionAck(ack)) =>
      contentionAckFromProto(ack)

    case rr.SBReachabilityReporterMsg(rr.SBReachabilityReporterMsg.Payload.Introduction(introduction)) =>
      introductionFromProto(introduction)

    case rr.SBReachabilityReporterMsg(rr.SBReachabilityReporterMsg.Payload.IntroductionAck(ack)) =>
      introductionAckFromProto(ack)

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

    rr.SBReachabilityReporterMsg().withContentionAck(contentionAckToProto(contentionAck)).toByteArray
  }

  private def contentionAckFromProto(contentionAck: rr.ContentionAck): ContentionAck = contentionAck match {
    case rr.ContentionAck(Some(to), Some(observer), Some(subject), Some(version)) =>
      ContentionAck(toUniqueAddress(to), toUniqueAddress(observer), toUniqueAddress(subject), version)

    case _ => throw new SerializationException(s"Missing fields in $contentionAck")
  }

  private def introductionToProtoByteArray(introduction: Introduction): Array[Byte] = {
    def introductionToProto(introduction: Introduction): rr.Introduction = introduction match {
      case Introduction(contentions) =>
        rr.Introduction()
          .withContentions {
            for {
              (subject, v)                                           <- contentions.toSeq
              (observer, ContentionAggregator(disagreeing, version)) <- v.toSeq
            } yield
              rr.Introduction
                .Record()
                .withSubject(toAkkaInternalProto(subject))
                .withObserver(toAkkaInternalProto(observer))
                .withDisagreeing(disagreeing.toSeq.map(toAkkaInternalProto))
                .withVersion(version)
          }
    }

    rr.SBReachabilityReporterMsg().withIntroduction(introductionToProto(introduction)).toByteArray
  }

  private def introductionFromProto(introduction: rr.Introduction): Introduction = introduction match {
    case rr.Introduction(contentions) =>
      val c = contentions.map {
        case rr.Introduction.Record(Some(subject), Some(observer), disagreeing, Some(version)) =>
          (toUniqueAddress(subject),
           (toUniqueAddress(observer),
            ContentionAggregator(
              disagreeing.map(toUniqueAddress)(collection.breakOut),
              version
            )))

        case record => throw new SerializationException(s"Missing fields in $record")
      }

      val contentionsGroupedBySubject = c.groupBy(_._1)

      val cs = contentionsGroupedBySubject.map {
        case (subject, vs) => subject -> vs.map(_._2).toMap // There is only one entry per observer-subject pair
      }

      Introduction(cs)
  }

  private def introductionAckToProtoByteArray(introductionAck: IntroductionAck): Array[Byte] = {
    def introductionAckToProto(introductionAck: IntroductionAck): rr.IntroductionAck = introductionAck match {
      case IntroductionAck(from) => rr.IntroductionAck().withFrom(toAkkaInternalProto(from))
    }

    rr.SBReachabilityReporterMsg().withIntroductionAck(introductionAckToProto(introductionAck)).toByteArray
  }

  private def introductionAckFromProto(introductionAck: rr.IntroductionAck): IntroductionAck = introductionAck match {
    case rr.IntroductionAck(Some(from)) => IntroductionAck(toUniqueAddress(from))
    case _                              => throw new SerializationException(s"Missing fields in $introductionAck")
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
