//package akka.cluster.sbr
//
//// TODO try to remove `Config` type param
//final class ConfiguredStrategy[A, Config](config: Config)(implicit ev: Strategy.Aux[A, Config]) {
//  def handle(worldView: WorldView): Either[Throwable, StrategyDecision] = ev.handle(worldView, config)
//}
