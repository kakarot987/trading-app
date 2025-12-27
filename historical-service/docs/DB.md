# Database Migrations

This service uses Flyway for database schema versioning and migrations.

## Migration Files Location

```
src/main/resources/db/migration/
├── V1__Create_candles_table.sql
├── V2__Create_aggregation_functions.sql
└── V3__Create_materialized_views.sql
```

## Migration Naming Convention

Flyway uses the following naming pattern:
```
V{version}__{description}.sql
```

Examples:
- `V1__Create_candles_table.sql` - Initial candles table
- `V2__Create_aggregation_functions.sql` - SQL functions
- `V3__Create_materialized_views.sql` - Materialized views

## What Each Migration Does

### V1: Create Candles Table
Creates the main `candles` table for storing OHLCV data with:
- UUID primary key
- Unique constraint on (symbol, interval, open_time)
- Indexes for fast queries
- Support for 1m, 5m, 15m, 1h, 1d intervals

**Note**: Does NOT create the `trades` table (created by Service 2)

### V2: Aggregation Functions
Creates PostgreSQL functions:
- `aggregate_trades_to_candles()` - Converts raw trades to candles
- `auto_aggregate_recent_candles()` - Scheduled aggregation job
- `cleanup_old_candles()` - Data retention cleanup

### V3: Materialized Views
Creates optimized views:
- `latest_candles` - Latest candle per symbol/interval
- Auto-refresh trigger on candle updates
- Indexes for fast lookups

## Running Migrations

### Automatic (Recommended)
Migrations run automatically on application startup:

```bash
./gradlew bootRun
```

Flyway will:
1. Check the `flyway_schema_history` table
2. Run any pending migrations in order
3. Record successful migrations

### Manual Migration
To run migrations manually without starting the app:

```bash
./gradlew flywayMigrate
```

### Other Flyway Commands

```bash
# View migration status
./gradlew flywayInfo

# Validate migrations
./gradlew flywayValidate

# Repair migration history (if needed)
./gradlew flywayRepair

# Clean database (DANGER - removes all objects)
./gradlew flywayClean  # Disabled in production
```

## Migration Order

✅ **Service 2 runs first** → Creates `trades` table
✅ **Service 4 runs after** → Creates `candles` table and reads from `trades`

## Important Notes

### 1. Shared Database Considerations

```
┌─────────────────┐
│   PostgreSQL    │
├─────────────────┤
│ trades (S2) ←───┼─── Service 2 (writes)
│                 │
│ candles (S4) ←──┼─── Service 4 (writes)
│                 │
│ trades (read)←──┼─── Service 4 (reads)
└─────────────────┘
```

- Service 2 owns `trades` table
- Service 4 owns `candles` table
- Service 4 reads from `trades` (created by Service 2)

### 2. Migration Failures

If a migration fails:
1. Check `flyway_schema_history` table
2. Fix the SQL error
3. Run `./gradlew flywayRepair` if needed
4. Re-run migrations

### 3. Rollback Strategy

Flyway doesn't support automatic rollback. To rollback:

**Option 1**: Create a new migration (recommended)
```sql
-- V4__Rollback_feature.sql
DROP TABLE IF EXISTS some_table;
```

**Option 2**: Manual rollback
```bash
# Restore from backup
psql -U postgres trading_db < backup.sql
```

## Configuration

### application.yaml

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true  # Handle existing databases
    baseline-version: 0
    validate-on-migrate: true
    clean-disabled: true       # Safety in production
```

### Environment Variables

```bash
# Override in production
SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/trading_db
SPRING_DATASOURCE_USERNAME=trading_user
SPRING_DATASOURCE_PASSWORD=secure_password
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
```

## Adding New Migrations

1. **Create new migration file**:
   ```bash
   touch src/main/resources/db/migration/V4__Add_index_to_candles.sql
   ```

2. **Write migration SQL**:
   ```sql
   -- V4__Add_index_to_candles.sql
   CREATE INDEX idx_candles_volume ON candles(volume DESC);
   ```

3. **Test locally**:
   ```bash
   ./gradlew flywayMigrate
   ./gradlew bootRun
   ```

4. **Deploy to production** - Flyway runs automatically

## Best Practices

✅ **DO**:
- Keep migrations small and focused
- Test migrations on dev/staging first
- Use meaningful version numbers and descriptions
- Add indexes for frequently queried columns
- Include rollback instructions in comments

❌ **DON'T**:
- Modify existing migration files after deployment
- Use `flywayClean` in production
- Create large migrations (split them up)
- Forget to commit migration files to Git

## Monitoring

### Check Migration Status

```sql
-- View migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Check for failed migrations
SELECT * FROM flyway_schema_history WHERE success = false;
```

### Logs

Flyway logs appear in application startup:
```
Flyway Community Edition 9.22.3
Successfully validated 3 migrations
Migrating schema "public" to version "1 - Create candles table"
Successfully applied 1 migration
```

## Troubleshooting

### Issue: "Migration checksum mismatch"
**Cause**: Migration file was modified after running
**Solution**:
```bash
./gradlew flywayRepair
```

### Issue: "Found non-empty schema without schema history table"
**Cause**: Existing database with objects
**Solution**: Enable baseline
```yaml
spring.flyway.baseline-on-migrate: true
```

### Issue: Migration stuck at "Running"
**Cause**: Database lock or long-running query
**Solution**:
```sql
-- Check locks
SELECT * FROM pg_locks WHERE NOT granted;

-- Kill blocking query
SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
WHERE state = 'active' AND query LIKE '%flyway%';
```

## Production Deployment Checklist

- [ ] Test migrations on staging environment
- [ ] Backup production database
- [ ] Review `flyway_schema_history` table
- [ ] Ensure database connection pooling is configured
- [ ] Set `spring.flyway.clean-disabled=true`
- [ ] Monitor migration logs during deployment
- [ ] Verify data integrity after migration
- [ ] Have rollback plan ready

## Schema Evolution Example

```
V1: Create candles table
    ↓
V2: Add aggregation functions
    ↓
V3: Add materialized views
    ↓
V4: Add new index (future)
    ↓
V5: Add new column (future)
```

Each version builds on the previous, ensuring consistent schema across environments.