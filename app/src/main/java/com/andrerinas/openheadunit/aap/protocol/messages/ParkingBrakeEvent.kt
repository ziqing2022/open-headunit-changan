package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.google.protobuf.Message

class ParkingBrakeEvent(isEngaged: Boolean)
    : SensorEvent(Sensors.SensorType.PARKING_BRAKE_VALUE, makeProto(isEngaged)) {

    companion object {
        private fun makeProto(isEngaged: Boolean): Message {
            return Sensors.SensorBatch.newBuilder().also { batch ->
                batch.addParkingbrakeData(
                    Sensors.SensorBatch.ParkingBrakeData.newBuilder().apply {
                        setIsEngaged(isEngaged)
                    }
                )
            }.build()
        }
    }
}
