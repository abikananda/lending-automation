# Safe Selenium Performance Changes

This branch intentionally limits optimization to behavior-equivalent Selenium mechanics plus fail-safe fixes for two critical swallowed UI failures.

## Changed

- Borrower card list is fetched once per batch and refreshed only after an actual `StaleElementReferenceException`.
- Scrolling no longer waits internally for scroll position changes because `BorrowerScraper` already waits for borrower-card count growth immediately afterward.
- Accordion expansion replaces the unconditional 150 ms sleep with a 50 ms polling wait for `aria-expanded=true`; if the fast wait times out, the original 150 ms compatibility delay is still applied.
- Popup closing uses the fast polling wait first, then retains the previous ultra-short fallback behavior.
- `ObjectMapper` is reused instead of recreated for every borrower; borrower logging format remains unchanged.
- Borrower-popup open failures now throw instead of continuing into parsing. This prevents a failed card click from allowing the parser to read a stale popup from a previous borrower.
- `Add Loan` click failures now throw instead of being swallowed. The click is still attempted exactly once. Because wallet reservation and selection bookkeeping happen only after the method returns, a failed click can no longer create a false local investment state.

## Explicitly unchanged

- Drools rules and thresholds
- borrower pre-check logic
- NPA/trusted/currently-lent borrower logic
- borrower-name `seenCards` identity behavior
- lending amounts and wallet calculations
- financial action retry behavior (`Add Loan` is not retried)
- DB reads/writes
- filter/sort workflow
- panel labels and extracted borrower fields

## Validation

This repository does not currently contain a GitHub Actions workflow. Run the existing Maven test suite and a non-live/local smoke run before using the branch for real-money investment.
