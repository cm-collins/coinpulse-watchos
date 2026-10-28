package com.example.coinpulse.data

import com.example.coinpulse.data.remote.ApiFailure
import com.example.coinpulse.domain.FailureReason

internal fun ApiFailure.toFailureReason(): FailureReason =
    when (this) {
        ApiFailure.Network -> FailureReason.Offline
        ApiFailure.RateLimited -> FailureReason.RateLimited
        ApiFailure.Client,
        ApiFailure.Server,
        ApiFailure.Malformed -> FailureReason.Unknown
    }
