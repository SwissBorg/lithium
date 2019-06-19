package com.swissborg.sbr.strategy.keepmajority

import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios._
import com.swissborg.sbr.strategy.Union
import com.swissborg.sbr.strategy.indirectlyconnected.IndirectlyConnected

import scala.util.Try

class KeepMajoritySpec extends SBSpec {
  import KeepMajoritySpec._

  "KeepMajority" must {
    simulate[Try, KeepMajority, CleanPartitionsScenario]("handle clean partitions")(_.get)

    simulate[Try, KeepMajority, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(_.get)

    simulate[Try, KeepMajorityWithIC, IndirectlyConnectedScenario]("handle non-clean partitions")(
      _.get
    )

    simulate[Try, KeepMajorityWithIC, WithIndirectlyConnected[OldestRemovedDisseminationScenario]](
      "handle non-clean partitions during the oldest-removed scenarios..."
    )(_.get)
  }
}

object KeepMajoritySpec {
  type KeepMajorityWithIC[F[_]] = Union[F, KeepMajority, IndirectlyConnected]
}
