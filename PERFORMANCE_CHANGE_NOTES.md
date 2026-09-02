# Safe Selenium Performance Changes

This branch intentionally limits optimization to behavior-equivalent Selenium mechanics and fail-safe UI lifecycle protections required for the real-money lending workflow.

## Changed

- Borrower card list is fetched once per batch and refreshed only after an actual `StaleElementReferenceException`.
- Scrolling no longer waits internally for scroll position changes because `BorrowerScraper` already waits for borrower-card count growth immediately afterward.
- Accordion expansion replaces the unconditional 150 ms sleep with a 50 ms polling wait for `aria-expanded=true`; if the fast wait times out, the original 150 ms compatibility delay is still applied.
- `ObjectMapper` is reused instead of recreated for every borrower; borrower logging format remains unchanged.
- Borrower popup closing is fail-closed: the previous popup must be confirmed gone before another borrower can be opened.
- If popup closure cannot be confirmed, the workflow aborts instead of continuing with uncertain UI state.
- Before opening a borrower card, the workflow verifies that no previous borrower popup remains present.
- Parsed popup borrower identity must match the card borrower name before rule evaluation or financial action.
- Duplicate loan IDs in the same scrape are treated as an unsafe stale-data condition and abort processing.

## Explicitly unchanged

- Drools rules and thresholds
- borrower pre-check logic
- NPA/trusted/currently-lent borrower logic
- lending amounts and wallet calculations
- Add Loan click count (exactly once)
- financial retry behavior
- DB reads/writes
- filter/sort workflow
- panel labels and extracted borrower fields

## Validation

This repository does not currently contain a GitHub Actions workflow. Run the existing Maven test suite and a non-live/local smoke run before using the branch for real-money investment.
