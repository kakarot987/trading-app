package com.trading.websocker_broadcaster.controller

import com.trading.common.model.MarketSnapshotResponse
import com.trading.websocker_broadcaster.service.MarketSnapshotService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/market")
class MarketSnapshotController(
    private val snapshotService: MarketSnapshotService
) {

    @GetMapping("/snapshot")
    fun snapshot(): MarketSnapshotResponse =
        MarketSnapshotResponse(snapshotService.getSnapshot())
}
