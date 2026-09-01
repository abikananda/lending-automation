# Safe Selenium Performance Changes

This branch intentionally limits optimization to behavior-equivalent Selenium mechanics.

## Changed

- Borrower card list is fetched once per batch and refreshed only after an actual `StaleElementReferenceException`.
- Scrolling no longer waits internally for scroll position changes because `BorrowerScraper` already waits for borrower-card count growth immediately afterward.
- Accordion expansion replaces the unconditional 150 ms sleep with a 50 ms polling wait for `aria-expanded=true`; if the fast wait times out, the original 150 ms compatibility delay is still applied.
- Popup closing uses the fast polling wait first, then retains the previous ultra-short fallback behavior.
- `ObjectMapper` is reused instead of recreated for every borrower; borrower logging format remains unchanged.

## Explicitly unchanged

- Drools rules and thresholds
- borrower pre-check logic
- NPA/trusted/currently-lent borrower logic
- borrower-name `seenCards` identity behavior
- lending amounts and wallet calculations
- Add Loan click behavior
- financial retry behavior
- DB reads/writes
- filter/sort workflow
- panel labels and extracted borrower fields

## Validation

This repository does not currently contain a GitHub Actions workflow. Run the existing Maven test suite and a non-live/local smoke run before using the branch for real-money investment.
