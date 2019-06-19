package com.swissborg.sbr.strategy.staticquorum

import cats.effect.SyncIO
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios._
import com.swissborg.sbr.strategy.Union
import com.swissborg.sbr.strategy.indirectlyconnected.IndirectlyConnected

class StaticQuorumSpec extends SBSpec {
  import StaticQuorumSpec._

  "StaticQuorum" must {
    simulate[SyncIO, StaticQuorum, CleanPartitionsScenario]("handle clean partitions")(
      _.unsafeRunSync()
    )

    simulate[SyncIO, StaticQuorum, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(
      _.unsafeRunSync()
    )

    simulate[SyncIO, StaticQuorum, UpDisseminationScenario]("handle split during up-dissemination")(
      _.unsafeRunSync()
    )

    simulate[SyncIO, StaticQuorumWithIC, IndirectlyConnectedScenario](
      "handle non-clean partitions"
    )(
      _.unsafeRunSync()
    )

    simulate[SyncIO, StaticQuorumWithIC, WithIndirectlyConnected[
      OldestRemovedDisseminationScenario
    ]](
      "handle non-clean partitions during the oldest-removed scenarios"
    )(
      _.unsafeRunSync()
    )

    simulate[SyncIO, StaticQuorumWithIC, WithIndirectlyConnected[UpDisseminationScenario]](
      "handle non-clean partitions during up-dissemination scenarios"
    )(
      _.unsafeRunSync()
    )
  }
}

object StaticQuorumSpec {
  type StaticQuorumWithIC[F[_]] = Union[F, StaticQuorum, IndirectlyConnected]
}
