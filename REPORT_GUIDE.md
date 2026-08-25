# 📊 Execution Report Generation Guide

## Quick Steps to Generate Report

### Step 1: Build the Project
```bash
cd lending-automation
mvn clean package -DskipTests
```

### Step 2: Run the Automation
```bash
mvn test -Dtest=P2PAutomation
```
Or run directly from your IDE:
- Right-click `P2PAutomation.java` → Run

### Step 3: Wait for Completion
The automation will:
- Execute all investment rules
- Process borrowers and finalize lending
- **Automatically generate report** at the end
- Close the browser

### Step 4: Find Your Report

#### Location: `reports/` folder in project root
```
lending-automation/
├── reports/
│   ├── execution_report_2026-08-09_00-30-15.html  ← OPEN THIS
│   ├── borrower_data_2026-08-09_00-30-15.csv
│   ├── execution_report_2026-08-09_01-45-22.html
│   └── borrower_data_2026-08-09_01-45-22.csv
```

---

## How to View the Report

### Option 1: Direct File Open (Recommended)
1. Navigate to: `lending-automation/reports/`
2. Find the latest `execution_report_*.html` file
3. **Double-click** to open in default browser
4. 🎉 Full interactive report opens!

### Option 2: Command Line
```powershell
# Windows
explorer "reports\execution_report_*.html"

# Or directly from Git Bash
start reports/execution_report_*.html
```

---

## Report Contents

### 📊 Summary Cards (Top Section)
```
✅ Rules Passed: 3/4
❌ Rules Failed: 1
👥 Borrowers Finalized: 12
💰 Total Lent: ₹45,000
⏱️ Execution Time: 02:35:18
💳 Wallet Balance: ₹5,000
```

### 📈 Charts & Visualizations
- **Rules Status Chart** - Doughnut showing pass/fail ratio
- **Borrowers Chart** - Bar chart of selected vs finalized
- **Status Breakdown** - Pie chart showing finalized/pending/failed

### 💰 Wallet Flow
```
Starting Balance (₹50,000) → Amount Lent (₹45,000) → Final Balance (₹5,000)
```

### 📋 Per-Rule Details Table
| Rule Name | Status | Selected | Finalized | Amount | Time | Remarks |
|-----------|--------|----------|-----------|--------|------|---------|
| Low Risk | ✅ PASSED | 8 | 8 | ₹20,000 | 00:45 | — |
| High Risk | ❌ FAILED | 5 | 0 | ₹0 | 01:20 | Slider failed |

### ⚠️ Errors Section
Shows any errors encountered during execution with full details.

---

## 📥 Download CSV Data

### Built-in Download Button
1. Open `execution_report_*.html` in browser
2. Scroll down to **"📥 Download Borrower Data"** section
3. Click **"⬇️ Download CSV"** button
4. File saves to your Downloads folder

### CSV Contents
```
Rule Name,Borrower Name,Borrowing Amount,Risk Rating,Status,Failure Reason,Selection Time (ms),Finalization Time (ms)
Low Risk,Arun Kumar,2000,4.5,FINALIZED,,1250,3500
Low Risk,Priya Sharma,2500,3.8,FINALIZED,,980,2800
High Risk,Raj Singh,5000,6.2,FAILED,Slider adjustment failed,2100,
```

---

## Real-Time Monitoring During Run

### Console Output
```
[main] INFO P2PAutomation - Started investment with: Low Risk Lenders
[main] INFO BorrowerScraper - ✅ Selected borrower: Arun Kumar for ₹2,000
[main] INFO LendingFinalizer - ✅ Lending finalized for ₹2,000
[main] INFO P2PAutomation - Completed investment with: Low Risk Lenders

... (repeats for each rule)

[main] INFO ReportGenerator - ✅ Report generated: reports/execution_report_2026-08-09_00-30-15.html
[main] INFO ReportGenerator - ✅ CSV exported: reports/borrower_data_2026-08-09_00-30-15.csv
```

---

## Troubleshooting

### Report Not Generated?
✅ **Check:** Is automation completed?
- Look for: `✅ WebDriver closed successfully`
- Look for: `📊 Execution report available at:`

✅ **Solution:** Run automation again with verbose logging
```bash
mvn test -Dtest=P2PAutomation -X
```

### Report is Blank?
✅ **Check:** No rules were executed
- Verify `config.properties` has rules configured
- Check wallet balance ≥ ₹250

### CSV won't Download?
✅ **Solution:** Download manually
- Open `reports/borrower_data_*.csv` directly in Excel
- Or download from link in HTML

---

## Example Report Workflow

```
START AUTOMATION
    ↓
Execute Rule 1 (Low Risk)
    ├─ Select 8 borrowers
    ├─ Finalize 8 lending
    └─ Update metrics
    ↓
Execute Rule 2 (High Risk)
    ├─ Select 5 borrowers
    ├─ Finalize 5 lending
    └─ Update metrics
    ↓
Execute Rule 3 (Business)
    ├─ Select 3 borrowers
    ├─ FAIL on slider → Update metrics with error
    ↓
Generate Report
    ├─ Aggregate all metrics
    ├─ Create HTML with charts
    ├─ Create CSV with borrower data
    └─ Save to reports/ folder
    ↓
Open in Browser
    ├─ View interactive dashboard
    ├─ Download CSV anytime
    └─ Review errors
```

---

## Automation Script (Optional)

### Create: `run_automation.sh`
```bash
#!/bin/bash
cd lending-automation

# Build and run
mvn clean package -DskipTests > /dev/null 2>&1
mvn test -Dtest=P2PAutomation

# Open report automatically
REPORT=$(ls -t reports/execution_report_*.html | head -1)
if [ -f "$REPORT" ]; then
    echo "Opening report: $REPORT"
    xdg-open "$REPORT"  # Linux
    # open "$REPORT"    # macOS
    # start "$REPORT"   # Windows
else
    echo "No report found!"
    exit 1
fi
```

### Run it:
```bash
chmod +x run_automation.sh
./run_automation.sh
```

---

## Key Metrics Tracked in Report

| Metric | Source | Example |
|--------|--------|---------|
| **Rules Executed** | P2PAutomation loop | 4 |
| **Rules Passed** | LendingOrchestrator success | 3 |
| **Rules Failed** | Exception handling | 1 |
| **Borrowers Selected** | BorrowerScraper | 16 |
| **Borrowers Finalized** | LendingFinalizer | 13 |
| **Total Amount Lent** | Investment.walletAmount delta | ₹45,000 |
| **Execution Time** | MethodTimer.getElapsedMillis() | 2:35:18 |
| **Wallet Before/After** | Initial vs Final | ₹50,000 → ₹5,000 |

---

## Report Retention

- Reports are **kept** in the `reports/` folder
- Each run generates new report with timestamp
- **Never overwrites** previous reports
- You can compare multiple runs side-by-side

### Cleanup (Optional)
```bash
# Remove old reports
rm reports/execution_report_*.html
rm reports/borrower_data_*.csv

# Keep only last 5 reports
ls -t reports/execution_report_*.html | tail -n +6 | xargs rm
```

---

## 🎉 That's It!

Your report is ready to view. Open the HTML file in any browser and explore the interactive dashboard!
