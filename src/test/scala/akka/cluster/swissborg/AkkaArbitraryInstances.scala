package akka.cluster.swissborg

import akka.cluster.{Member, UniqueAddress, Reachability => _}
import com.swissborg.sbr.ArbitraryInstances.arbUniqueAddress
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import shapeless.tag
import shapeless.tag.@@

object AkkaArbitraryInstances {
  sealed trait JoiningTag
  type JoiningMember = Member @@ JoiningTag

  implicit val arbJoiningMember: Arbitrary[JoiningMember] = Arbitrary {
    for {
      uniqueAddress <- arbitrary[UniqueAddress]
    } yield tag[JoiningTag][Member](Member(uniqueAddress, Set("dc-datacenter")))
  }
}
