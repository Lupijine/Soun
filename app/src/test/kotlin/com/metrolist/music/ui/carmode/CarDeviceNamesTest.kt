package com.metrolist.music.ui.carmode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDeviceNamesTest {
    @Test fun `car head units are recognised by name`() {
        listOf(
            "MY CAR", "Car_BT", "MyCar", "CAR MULTIMEDIA", "Echo Auto", "VW BT 4523", "BMW12345", "Audi MMI 7781",
            "Toyota Touch", "PEUGEOT208", "Citroën", "MEDIA-NAV", "Dacia", "R-LINK", "Uconnect 2041", "SYNC",
            "Kia Motors", "Hyundai", "Mercedes-Benz", "Vehicle 12", "CarKit", "Ford Fiesta", "SEAT Leon",
        ).forEach { assertTrue(it, CarDeviceNames.looksLikeCar(it)) }
    }

    @Test fun `ordinary headphones and speakers are not cars`() {
        listOf(
            null, "", "  ", "AirPods Pro", "Galaxy Buds2 Pro", "JBL Flip 5", "Bose QC35 II", "Soundcore Life Q30",
            "Sony WH-1000XM4", "Bose SoundLink Mini", "Card reader", "Oscar's headphones", "Pixel Buds", "Audio Box",
            "Scarlett Speaker", "Automatic Speaker", "Carbon Buds",
        ).forEach { assertFalse(it.toString(), CarDeviceNames.looksLikeCar(it)) }
    }
}
