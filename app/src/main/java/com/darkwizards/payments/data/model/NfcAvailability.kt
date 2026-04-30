package com.darkwizards.payments.data.model

sealed class NfcAvailability {
    object Available : NfcAvailability()
    object NoHardware : NfcAvailability()
    object Disabled : NfcAvailability()
}
