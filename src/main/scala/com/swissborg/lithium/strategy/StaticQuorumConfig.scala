package com.swissborg.lithium.strategy

import com.typesafe.config.Config

/**
 * [[StaticQuorum]] config.
 *
 * @param quorumSize the minimum number of nodes the surviving partition must contain.
 *                   The size must be chosen as more than `number-of-nodes / 2 + 1`.
 * @param role       the role of the nodes to take in account.
 */
final case class StaticQuorumConfig(role: String, quorumSize: Int)

object StaticQuorumConfig extends StrategyConfig[StaticQuorumConfig] {
  override val name: String = "static-quorum"

  override def fromConfig(config: Config): StaticQuorumConfig = {
    val prefix = s"com.swissborg.lithium.${name}"

    val role       = config.getString(s"${prefix}.role")
    val quorumSize = config.getInt(s"${prefix}.quorum-size")
    if (quorumSize <= 0) {
      throw new IllegalArgumentException("Quorum size must be > 0")
    }

    StaticQuorumConfig(role, quorumSize)
  }
}
