# Borrower popup hot-path optimization

Observed from the 2026-09-03 live run:

- 79 parsed borrowers averaged about 12.0 seconds from `Opening borrower` to the parsed `Borrower` log.
- 75 rejected/existing borrowers averaged about 20.33 seconds from the parsed `Borrower` log to the `Popup closed` log.
- The repeated timing strongly indicated WebDriver round-trip overhead in the borrower popup lifecycle rather than Drools or Java rule evaluation.

This change batches borrower popup DOM lookup, arrow lookup, scroll and click into one browser-side JavaScript call. Popup close lookup and click are similarly batched. Visibility/removal confirmation remains fail-closed.

Unchanged safety behavior:

- Add Loan is never retried.
- Continue/finalization is never retried.
- stale-card and popup-timeout recovery remains owned by BorrowerScraper.
- borrower card/popup identity validation remains mandatory.
- duplicate loanId still aborts.
- NPA/trusted/currently-lent checks, Drools rules, lending amounts and wallet accounting are unchanged.
