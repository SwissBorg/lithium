package akka.cluster.sbr

import akka.remote.testconductor.RoleName

object util {
  def linksToKillForPartitions(partitions: List[List[RoleName]]): List[(RoleName, RoleName)] = {
    def go(partitions: List[List[RoleName]], links: List[(RoleName, RoleName)]): List[(RoleName, RoleName)] =
      partitions match {
        case Nil      => links
        case _ :: Nil => links
        case p :: ps =>
          val others = ps.flatten
          val newLinks = p.foldLeft(List.empty[(RoleName, RoleName)]) {
            case (links, role) => links ++ others.map(_ -> role)
          }

          go(ps, links ++ newLinks)
      }

    go(partitions, List.empty)
  }
}
