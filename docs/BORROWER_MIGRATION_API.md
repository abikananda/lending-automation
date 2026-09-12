# Borrower migration API

The Selenium project exposes its historical `borrower_loans` records only on localhost and
requires `X-Migration-Key` on every request.

Set `BORROWER_MIGRATION_API_KEY` to a private value in your environment, then run
`com.abika.migration.BorrowerMigrationApi`. The default address is `127.0.0.1:8090`.

## Endpoints

```http
GET /api/migration/borrowers/ids?afterId=0&limit=100
X-Migration-Key: <migration-key>
```

Returns a cursor page of source row IDs.

```http
GET /api/migration/borrowers/{id}
X-Migration-Key: <migration-key>
```

Returns one complete historical borrower. The risk engine intentionally fetches these records
one at a time so a failed run can resume safely from the last processed ID.
