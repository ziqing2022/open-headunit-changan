package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.google.protobuf.Message

class CarSpeedEvent(
    speedMps: Float,
    cruiseEngaged: Boolean = false,
    cruiseSetSpeedMps: Float? = null,
) : SensorEvent(Sensors.SensorType.CAR_SPEED_VALUE, makeProto(speedMps, cruiseEngaged, cruiseSetSpeedMps)) {

    companion object {
        private fun makeProto(
            speedMps: Float,
            cruiseEngaged: Boolean,
            cruiseSetSpeedMps: Float?,
        ): Message {
            return Sensors.SensorBatch.newBuilder().also { batch ->
                batch.addSpeedData(
                    Sensors.SensorBatch.SpeedData.newBuilder().apply {
                        speedE3 = (speedMps * 1000f).toInt().coerceAtLeast(0)
                        if (cruiseEngaged) {
                            setCruiseEngaged(true)
                            cruiseSetSpeedMps?.let { setCruiseSetSpeed((it * 1000f).toInt()) }
                        }
                    }
                )
            }.build()
        }
    }
}
