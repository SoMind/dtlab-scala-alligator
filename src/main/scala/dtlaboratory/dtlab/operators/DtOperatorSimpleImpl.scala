package dtlaboratory.dtlab.operators

import java.time.ZonedDateTime
import com.typesafe.scalalogging.LazyLogging
import dtlaboratory.dtlab.models.{DtState, Operator, Telemetry}

import scala.concurrent.duration.TimeUnit

/**
 * simple operators are non-blocking stateless functions that only need
 * new telemetry, old state, and static parameters to do their work
 */
trait DtOperatorSimpleImpl extends DtOperatorImpl with LazyLogging {

  /**
    * Keep calculating the duration of the DT.
    *
    * It is a bit convoluted - it creates a recording of the first sighting
    * and then consults that sighting in all successive calls.  The purpose
    * is partly to show how complex state can be advanced using only the
    * telemetry object, actor type, and operator as building blocks.  You
    * could implement complex logic with complex intermediate state in a
    * non-blocking high performance way using this pattern.
    *
    * input:
    *   idx 0. idx of the telemetry we are watching
    * two outputs:
    *   idx 0. idx of the calculation we are making
    *   idx 1. idx first time we see the telemetry
    */
  def activeDuration(telemetry: Telemetry,
                     dtState: DtState,
                     op: Operator,
                     unit: TimeUnit): List[Telemetry] = {

    op match {
      case _ if op.input.length < 1 =>
        logger.error(s"operator '${op.name}' does not have proper inputs: ${op.input}")
        List()
      case _ if op.output.length < 2 =>
        logger.error(s"operator '${op.name}' does not have proper outputs: ${op.output}")
        List()
      case _ =>
        val prevState: Option[Telemetry] = dtState.state.get(op.output.head)
        val initTime: Option[Telemetry] = dtState.state.get(op.output(1))
        val initDate: ZonedDateTime = initTime
          .map(_.datetime.getOrElse(ZonedDateTime.now()))
          .getOrElse(ZonedDateTime.now())

        val lastCount = prevState.map(_.value).getOrElse(0.0)
        val newCount = unit.toChronoUnit
          .between(initDate, telemetry.datetime.getOrElse(ZonedDateTime.now()))

        if (newCount > lastCount || prevState.isEmpty || initTime.isEmpty) {
          val t = Telemetry(op.output.head, newCount)
          if (initTime.isEmpty)
            List(t, Telemetry(op.output(1), 1))
          else
            List(t)
        } else
          List()

    }
  }

  def applyImplementation(telemetry: Telemetry,
                          dtState: DtState,
                          op: Operator): List[Telemetry]

  // entry point
  override def apply(telemetry: Telemetry,
                     dtState: DtState,
                     op: Operator): List[Telemetry] = {
    op match {

      case _ if op.input.isEmpty =>
        logger.warn(s"operator '$op' does not specify input")
        List()

      case _ if op.output.isEmpty =>
        logger.warn(s"operator '$op' does not specify output")
        List()

      case _ if !op.input.contains(telemetry.idx) =>
        // just a sanity check - this should never happen - runtime uses op to select telemetry
        logger.warn(
          s"telemetry $telemetry does not match specified input for op '$op'")
        List()

      case _ =>
        logger.debug(
          s"applying operator ${op.name} for state idx ${telemetry.idx}")
        applyImplementation(telemetry, dtState, op)

    }
  }
}
