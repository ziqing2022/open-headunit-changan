package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.google.protobuf.Message

class GearEvent(gear: Sensors.SensorBatch.GearData.GEAR)
    : SensorEvent(Sensors.SensorType.GEAR_VALUE, makeProto(gear)) {

    companion object {
        private fun makeProto(gear: Sensors.SensorBatch.GearData.GEAR): Message {
            return Sensors.SensorBatch.newBuilder().also { batch ->
                batch.addGearData(
                    Sensors.SensorBatch.GearData.newBuilder().apply {
                        setGear(gear)
                    }
                )
            }.build()
        }
    }
}
