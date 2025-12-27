package com.trading.trade_processor.repository

import com.trading.trade_processor.entity.TradeEntity
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TradeRepository : ReactiveCrudRepository<TradeEntity, String>