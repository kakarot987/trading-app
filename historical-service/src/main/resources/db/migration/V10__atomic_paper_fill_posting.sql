-- A single PostgreSQL statement/transaction atomically posts a simulated fill.
-- Locks the wallet first so concurrent paper fills cannot overspend balances.
-- No exchange connectivity or live order execution is permitted here.
CREATE FUNCTION paper_post_fill(
  p_order_id UUID, p_tick_time_ms BIGINT,
  p_price NUMERIC(38,8), p_gross NUMERIC(38,8), p_fee NUMERIC(38,8)
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
  o paper_orders%ROWTYPE;
  v_fill UUID;
  v_base VARCHAR(8);
  v_base_delta NUMERIC(38,8);
  v_quote_delta NUMERIC(38,8);
  v_available NUMERIC(38,8);
BEGIN
  -- Read wallet identity without locking order first: consistent wallet lock order.
  SELECT * INTO o FROM paper_orders WHERE order_id=p_order_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Unknown paper order'; END IF;
  PERFORM 1 FROM paper_wallets WHERE wallet_id=o.wallet_id FOR UPDATE;
  SELECT * INTO o FROM paper_orders WHERE order_id=p_order_id FOR UPDATE;
  IF o.status = 'FILLED' THEN RETURN FALSE; END IF;
  IF o.status <> 'NEW' THEN RAISE EXCEPTION 'Paper order is terminal'; END IF;
  IF p_tick_time_ms <= o.submitted_at_ms OR p_price <= 0 OR p_gross <= 0
     OR p_fee < 0 OR p_fee > p_gross THEN
    RAISE EXCEPTION 'Invalid future-tick paper fill';
  END IF;
  IF p_gross <> round(o.quantity * p_price,8) THEN
    RAISE EXCEPTION 'Inconsistent paper fill gross';
  END IF;
  v_base := replace(o.symbol,'USDT','');
  IF o.side='BUY' THEN
    v_base_delta := o.quantity;
    v_quote_delta := -p_gross-p_fee;
  ELSE
    v_base_delta := -o.quantity;
    v_quote_delta := p_gross-p_fee;
  END IF;
  -- Create missing zero rows, then acquire stable per-asset row locks.
  INSERT INTO paper_balances(wallet_id,asset,available)
  VALUES (o.wallet_id,v_base,0),(o.wallet_id,'USDT',0)
  ON CONFLICT DO NOTHING;
  PERFORM 1 FROM paper_balances
    WHERE wallet_id=o.wallet_id AND asset IN (v_base,'USDT')
    ORDER BY asset FOR UPDATE;
  SELECT available INTO v_available FROM paper_balances
    WHERE wallet_id=o.wallet_id AND asset=v_base;
  IF v_available+v_base_delta < 0 THEN RAISE EXCEPTION 'Insufficient paper base balance'; END IF;
  SELECT available INTO v_available FROM paper_balances
    WHERE wallet_id=o.wallet_id AND asset='USDT';
  IF v_available+v_quote_delta < 0 THEN RAISE EXCEPTION 'Insufficient paper USDT balance'; END IF;
  INSERT INTO paper_fills(order_id,tick_time_ms,quantity,execution_price,gross_quote,fee_quote)
    VALUES(o.order_id,p_tick_time_ms,o.quantity,p_price,p_gross,p_fee)
    RETURNING fill_id INTO v_fill;
  UPDATE paper_balances SET available=available+v_base_delta
    WHERE wallet_id=o.wallet_id AND asset=v_base;
  UPDATE paper_balances SET available=available+v_quote_delta
    WHERE wallet_id=o.wallet_id AND asset='USDT';
  INSERT INTO paper_ledger_entries(wallet_id,fill_id,asset,amount)
    VALUES(o.wallet_id,v_fill,v_base,v_base_delta),(o.wallet_id,v_fill,'USDT',v_quote_delta);
  UPDATE paper_orders SET status='FILLED' WHERE order_id=o.order_id;
  RETURN TRUE;
END;
$$;
