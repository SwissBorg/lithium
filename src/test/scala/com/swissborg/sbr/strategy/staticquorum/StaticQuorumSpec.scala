package com.swissborg.sbr.strategy.staticquorum

import cats.effect.SyncIO
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios._

class StaticQuorumSpec extends SBSpec {
  "StaticQuorum" must {
    simulate[SyncIO, StaticQuorum, SymmetricSplitScenario](
      "handle symmetric split scenarios with a correctly defined quorum size"
    )(_.unsafeRunSync())

    simulate[SyncIO, StaticQuorum, OldestRemovedScenario](
      "handle a split during the oldest-removed scenarios")(
      _.unsafeRunSync()
    )

    simulate[SyncIO, StaticQuorum, UpDisseminationScenario]("handle split during up-dissemination")(
      _.unsafeRunSync())
  }
}
