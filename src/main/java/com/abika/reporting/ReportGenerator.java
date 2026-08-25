package com.abika.reporting;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

public class ReportGenerator {
    private static final String REPORT_DIR = "reports";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ReportGenerator() {
        new File(REPORT_DIR).mkdirs();
    }

    public String generateReport(ExecutionMetrics metrics) throws Exception {
        String timestamp = metrics.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String htmlFileName = REPORT_DIR + "/execution_report_" + timestamp + ".html";
        String csvFileName = REPORT_DIR + "/borrower_data_" + timestamp + ".csv";

        String csvContent = generateCSV(metrics);
        String base64CSV = Base64.getEncoder().encodeToString(csvContent.getBytes());

        String htmlContent = generateHTML(metrics, base64CSV, "borrower_data_" + timestamp + ".csv");
        
        Files.write(Paths.get(htmlFileName), htmlContent.getBytes());
        Files.write(Paths.get(csvFileName), csvContent.getBytes());

        System.out.println("✅ Report generated: " + htmlFileName);
        System.out.println("✅ CSV exported: " + csvFileName);
        
        return htmlFileName;
    }

    private String generateCSV(ExecutionMetrics metrics) {
        StringBuilder csv = new StringBuilder();
        csv.append("Rule Name,Borrower Name,Borrowing Amount,Risk Rating,Status,Failure Reason,Selection Time (ms),Finalization Time (ms)\n");

        for (ExecutionMetrics.BorrowerRecord record : metrics.getBorrowerRecords()) {
            csv.append(escapeCSV(record.getRuleName())).append(",");
            csv.append(escapeCSV(record.getBorrowerName())).append(",");
            csv.append(record.getBorrowingAmount()).append(",");
            csv.append(record.getRiskRating() != null ? record.getRiskRating() : "N/A").append(",");
            csv.append(record.getStatus()).append(",");
            csv.append(escapeCSV(record.getFailureReason() != null ? record.getFailureReason() : "")).append(",");
            csv.append(record.getSelectionTimeMs() != null ? record.getSelectionTimeMs() : "N/A").append(",");
            csv.append(record.getFinalizationTimeMs() != null ? record.getFinalizationTimeMs() : "N/A").append("\n");
        }

        return csv.toString();
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String generateHTML(ExecutionMetrics metrics, String base64CSV, String csvFileName) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>P2P Lending Automation - Execution Report</title>\n");
        html.append("    <script src=\"https://cdn.jsdelivr.net/npm/chart.js@3.9.1/dist/chart.min.js\"></script>\n");
        html.append("    <style>\n");
        html.append(getCSS());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");

        html.append(getHeader(metrics));
        html.append(getSummaryCards(metrics));
        html.append(getChartsSection(metrics));
        html.append(getRuleDetailsSection(metrics));
        html.append(getErrorsSection(metrics));
        html.append(getCSVSection(base64CSV, csvFileName));
        html.append(getFooter());

        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append(getChartScripts(metrics));
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    private String getCSS() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #333; padding: 20px; }\n" +
                ".container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 12px; box-shadow: 0 10px 40px rgba(0,0,0,0.3); overflow: hidden; }\n" +
                ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px 30px; text-align: center; }\n" +
                ".header h1 { font-size: 2.5em; margin-bottom: 10px; }\n" +
                ".header p { font-size: 1.1em; opacity: 0.9; }\n" +
                ".timestamp { font-size: 0.9em; opacity: 0.8; margin-top: 10px; }\n" +
                ".summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; padding: 30px; background: #f8f9fa; }\n" +
                ".card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-left: 5px solid #667eea; }\n" +
                ".card.success { border-left-color: #28a745; }\n" +
                ".card.warning { border-left-color: #ffc107; }\n" +
                ".card.danger { border-left-color: #dc3545; }\n" +
                ".card.info { border-left-color: #17a2b8; }\n" +
                ".card-label { font-size: 0.9em; color: #666; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 8px; }\n" +
                ".card-value { font-size: 2em; font-weight: bold; color: #333; }\n" +
                ".card-subtext { font-size: 0.85em; color: #999; margin-top: 8px; }\n" +
                ".section { padding: 30px; border-top: 1px solid #e9ecef; }\n" +
                ".section-title { font-size: 1.8em; font-weight: 600; margin-bottom: 20px; color: #333; display: flex; align-items: center; }\n" +
                ".section-title:before { content: ''; display: inline-block; width: 4px; height: 30px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); margin-right: 15px; border-radius: 2px; }\n" +
                ".charts-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 30px; margin-top: 20px; }\n" +
                ".chart-container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); position: relative; }\n" +
                ".table-responsive { overflow-x: auto; margin-top: 20px; }\n" +
                "table { width: 100%; border-collapse: collapse; }\n" +
                "th { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 12px; text-align: left; font-weight: 600; }\n" +
                "td { padding: 12px; border-bottom: 1px solid #e9ecef; }\n" +
                "tr:hover { background: #f8f9fa; }\n" +
                ".status-badge { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 0.85em; font-weight: 600; }\n" +
                ".status-success { background: #d4edda; color: #155724; }\n" +
                ".status-pending { background: #fff3cd; color: #856404; }\n" +
                ".status-failed { background: #f8d7da; color: #721c24; }\n" +
                ".error-box { background: #f8d7da; border-left: 4px solid #dc3545; padding: 15px; margin: 10px 0; border-radius: 4px; color: #721c24; }\n" +
                ".csv-section { text-align: center; padding: 30px; background: #f0f7ff; border-radius: 8px; margin: 20px 0; }\n" +
                ".csv-section h3 { color: #0056b3; margin-bottom: 15px; }\n" +
                ".btn { display: inline-block; padding: 12px 30px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; text-decoration: none; border-radius: 25px; font-weight: 600; cursor: pointer; border: none; font-size: 1em; transition: transform 0.2s, box-shadow 0.2s; }\n" +
                ".btn:hover { transform: translateY(-2px); box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4); }\n" +
                ".footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 0.9em; border-top: 1px solid #e9ecef; }\n" +
                ".wallet-flow { background: #e7f3ff; padding: 20px; border-radius: 8px; margin: 20px 0; }\n" +
                ".flow-item { display: flex; align-items: center; justify-content: space-around; flex-wrap: wrap; gap: 20px; }\n" +
                ".flow-box { background: white; padding: 15px 25px; border-radius: 8px; border: 2px solid #667eea; min-width: 150px; text-align: center; }\n" +
                ".flow-box-label { font-size: 0.85em; color: #666; text-transform: uppercase; }\n" +
                ".flow-box-value { font-size: 1.5em; font-weight: bold; color: #667eea; margin-top: 5px; }\n" +
                ".arrow { color: #667eea; font-size: 1.5em; }\n";
    }

    private String getHeader(ExecutionMetrics metrics) {
        return "<div class=\"header\">\n" +
                "    <h1>📊 P2P Lending Automation Report</h1>\n" +
                "    <p>Execution Summary & Performance Metrics</p>\n" +
                "    <div class=\"timestamp\">Generated: " + metrics.getEndTime().format(DATE_FORMATTER) + "</div>\n" +
                "</div>\n";
    }

    private String getSummaryCards(ExecutionMetrics metrics) {
        StringBuilder cards = new StringBuilder("<div class=\"summary-cards\">\n");
        
        cards.append("    <div class=\"card success\">\n");
        cards.append("        <div class=\"card-label\">✅ Rules Passed</div>\n");
        cards.append("        <div class=\"card-value\">").append(metrics.getTotalRulesPassed()).append("/").append(metrics.getTotalRulesExecuted()).append("</div>\n");
        cards.append("    </div>\n");

        cards.append("    <div class=\"card danger\">\n");
        cards.append("        <div class=\"card-label\">❌ Rules Failed</div>\n");
        cards.append("        <div class=\"card-value\">").append(metrics.getTotalRulesFailed()).append("</div>\n");
        cards.append("    </div>\n");

        cards.append("    <div class=\"card info\">\n");
        cards.append("        <div class=\"card-label\">👥 Borrowers Finalized</div>\n");
        cards.append("        <div class=\"card-value\">").append(metrics.getTotalBorrowersFinalized()).append("</div>\n");
        cards.append("        <div class=\"card-subtext\">Selected: ").append(metrics.getTotalBorrowersSelected()).append("</div>\n");
        cards.append("    </div>\n");

        cards.append("    <div class=\"card success\">\n");
        cards.append("        <div class=\"card-label\">💰 Total Lent</div>\n");
        cards.append("        <div class=\"card-value\">₹").append(String.format("%.0f", metrics.getTotalDeducted() != null ? metrics.getTotalDeducted() : 0.0)).append("</div>\n");
        cards.append("    </div>\n");

        cards.append("    <div class=\"card warning\">\n");
        cards.append("        <div class=\"card-label\">⏱️ Execution Time</div>\n");
        cards.append("        <div class=\"card-value\">").append(metrics.getFormattedDuration()).append("</div>\n");
        cards.append("    </div>\n");

        cards.append("    <div class=\"card info\">\n");
        cards.append("        <div class=\"card-label\">💳 Wallet Balance</div>\n");
        cards.append("        <div class=\"card-value\">₹").append(String.format("%.0f", metrics.getFinalWallet() != null ? metrics.getFinalWallet() : 0.0)).append("</div>\n");
        cards.append("    </div>\n");

        cards.append("</div>\n");
        return cards.toString();
    }

    private String getChartsSection(ExecutionMetrics metrics) {
        StringBuilder charts = new StringBuilder();
        charts.append("<div class=\"section\">\n");
        charts.append("    <div class=\"section-title\">📈 Analytics & Charts</div>\n");
        charts.append("    <div class=\"charts-grid\">\n");
        charts.append("        <div class=\"chart-container\">\n");
        charts.append("            <canvas id=\"rulesChart\"></canvas>\n");
        charts.append("        </div>\n");
        charts.append("        <div class=\"chart-container\">\n");
        charts.append("            <canvas id=\"borrowersChart\"></canvas>\n");
        charts.append("        </div>\n");
        charts.append("        <div class=\"chart-container\">\n");
        charts.append("            <canvas id=\"statusChart\"></canvas>\n");
        charts.append("        </div>\n");
        charts.append("    </div>\n");
        charts.append("</div>\n");

        // Wallet flow
        charts.append("<div class=\"section\">\n");
        charts.append("    <div class=\"section-title\">💰 Wallet Flow</div>\n");
        charts.append("    <div class=\"wallet-flow\">\n");
        charts.append("        <div class=\"flow-item\">\n");
        charts.append("            <div class=\"flow-box\">\n");
        charts.append("                <div class=\"flow-box-label\">Starting Balance</div>\n");
        charts.append("                <div class=\"flow-box-value\">₹").append(String.format("%.0f", metrics.getInitialWallet() != null ? metrics.getInitialWallet() : 0.0)).append("</div>\n");
        charts.append("            </div>\n");
        charts.append("            <div class=\"arrow\">→</div>\n");
        charts.append("            <div class=\"flow-box\">\n");
        charts.append("                <div class=\"flow-box-label\">Amount Lent</div>\n");
        charts.append("                <div class=\"flow-box-value\">-₹").append(String.format("%.0f", metrics.getTotalDeducted() != null ? metrics.getTotalDeducted() : 0.0)).append("</div>\n");
        charts.append("            </div>\n");
        charts.append("            <div class=\"arrow\">→</div>\n");
        charts.append("            <div class=\"flow-box\">\n");
        charts.append("                <div class=\"flow-box-label\">Final Balance</div>\n");
        charts.append("                <div class=\"flow-box-value\">₹").append(String.format("%.0f", metrics.getFinalWallet() != null ? metrics.getFinalWallet() : 0.0)).append("</div>\n");
        charts.append("            </div>\n");
        charts.append("        </div>\n");
        charts.append("    </div>\n");
        charts.append("</div>\n");

        return charts.toString();
    }

    private String getRuleDetailsSection(ExecutionMetrics metrics) {
        StringBuilder section = new StringBuilder();
        section.append("<div class=\"section\">\n");
        section.append("    <div class=\"section-title\">📋 Per-Rule Details</div>\n");
        section.append("    <div class=\"table-responsive\">\n");
        section.append("        <table>\n");
        section.append("            <thead>\n");
        section.append("                <tr>\n");
        section.append("                    <th>Rule Name</th>\n");
        section.append("                    <th>Status</th>\n");
        section.append("                    <th>Borrowers Selected</th>\n");
        section.append("                    <th>Borrowers Finalized</th>\n");
        section.append("                    <th>Amount Lent</th>\n");
        section.append("                    <th>Execution Time</th>\n");
        section.append("                    <th>Remarks</th>\n");
        section.append("                </tr>\n");
        section.append("            </thead>\n");
        section.append("            <tbody>\n");

        for (ExecutionMetrics.RuleMetrics rule : metrics.getRuleMetrics()) {
            section.append("                <tr>\n");
            section.append("                    <td><strong>").append(rule.getRuleName()).append("</strong></td>\n");
            section.append("                    <td>\n");
            String statusClass = rule.isPassed() ? "status-success" : "status-failed";
            String statusText = rule.isPassed() ? "✅ PASSED" : "❌ FAILED";
            section.append("                        <span class=\"status-badge ").append(statusClass).append("\">").append(statusText).append("</span>\n");
            section.append("                    </td>\n");
            section.append("                    <td>").append(rule.getBorrowersSelected()).append("</td>\n");
            section.append("                    <td>").append(rule.getBorrowersFinalized()).append("</td>\n");
            section.append("                    <td>₹").append(String.format("%.0f", rule.getAmountLent())).append("</td>\n");
            section.append("                    <td>").append(rule.getFormattedDuration()).append("</td>\n");
            section.append("                    <td>").append(rule.getFailureReason() != null ? rule.getFailureReason() : "—").append("</td>\n");
            section.append("                </tr>\n");
        }

        section.append("            </tbody>\n");
        section.append("        </table>\n");
        section.append("    </div>\n");
        section.append("</div>\n");

        return section.toString();
    }

    private String getErrorsSection(ExecutionMetrics metrics) {
        StringBuilder section = new StringBuilder();
        if (metrics.getErrors().isEmpty()) {
            return section.toString();
        }

        section.append("<div class=\"section\">\n");
        section.append("    <div class=\"section-title\">⚠️ Errors Encountered</div>\n");
        
        for (String error : metrics.getErrors()) {
            section.append("    <div class=\"error-box\">").append(escapeHtml(error)).append("</div>\n");
        }

        section.append("</div>\n");
        return section.toString();
    }

    private String getCSVSection(String base64CSV, String csvFileName) {
        return "<div class=\"section\">\n" +
                "    <div class=\"section-title\">📥 Download Borrower Data</div>\n" +
                "    <div class=\"csv-section\">\n" +
                "        <h3>📊 Borrower Data in CSV Format</h3>\n" +
                "        <p>All borrower records with their status and transaction details</p>\n" +
                "        <a href=\"data:text/csv;base64," + base64CSV + "\" download=\"" + csvFileName + "\" class=\"btn\">⬇️ Download CSV</a>\n" +
                "    </div>\n" +
                "</div>\n";
    }

    private String getFooter() {
        return "<div class=\"footer\">\n" +
                "    <p>P2P Lending Automation System | Report Generated Automatically</p>\n" +
                "    <p>© 2026 - All Rights Reserved</p>\n" +
                "</div>\n";
    }

    private String getChartScripts(ExecutionMetrics metrics) {
        StringBuilder scripts = new StringBuilder();

        int passed = metrics.getTotalRulesPassed();
        int failed = metrics.getTotalRulesFailed();

        scripts.append("const rulesCtx = document.getElementById('rulesChart').getContext('2d');\n");
        scripts.append("new Chart(rulesCtx, {\n");
        scripts.append("    type: 'doughnut',\n");
        scripts.append("    data: {\n");
        scripts.append("        labels: ['✅ Passed', '❌ Failed'],\n");
        scripts.append("        datasets: [{\n");
        scripts.append("            data: [").append(passed).append(", ").append(failed).append("],\n");
        scripts.append("            backgroundColor: ['#28a745', '#dc3545'],\n");
        scripts.append("            borderColor: ['#fff', '#fff'],\n");
        scripts.append("            borderWidth: 2\n");
        scripts.append("        }]\n");
        scripts.append("    },\n");
        scripts.append("    options: { responsive: true, plugins: { legend: { position: 'bottom', labels: { font: { size: 12 } } }, title: { display: true, text: 'Rules Execution Status' } } }\n");
        scripts.append("});\n\n");

        int selected = metrics.getTotalBorrowersSelected();
        int finalized = metrics.getTotalBorrowersFinalized();

        scripts.append("const borrowersCtx = document.getElementById('borrowersChart').getContext('2d');\n");
        scripts.append("new Chart(borrowersCtx, {\n");
        scripts.append("    type: 'bar',\n");
        scripts.append("    data: {\n");
        scripts.append("        labels: ['Selected', 'Finalized'],\n");
        scripts.append("        datasets: [{\n");
        scripts.append("            label: 'Borrowers',\n");
        scripts.append("            data: [").append(selected).append(", ").append(finalized).append("],\n");
        scripts.append("            backgroundColor: ['#17a2b8', '#28a745'],\n");
        scripts.append("            borderColor: ['#0c5460', '#155724'],\n");
        scripts.append("            borderWidth: 1\n");
        scripts.append("        }]\n");
        scripts.append("    },\n");
        scripts.append("    options: { responsive: true, indexAxis: 'y', plugins: { legend: { display: false }, title: { display: true, text: 'Borrower Processing' } } }\n");
        scripts.append("});\n\n");

        int finalizedCount = (int) metrics.getBorrowerRecords().stream().filter(r -> "FINALIZED".equals(r.getStatus())).count();
        int failedCount = (int) metrics.getBorrowerRecords().stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        int selectedCount = (int) metrics.getBorrowerRecords().stream().filter(r -> "SELECTED".equals(r.getStatus())).count();

        scripts.append("const statusCtx = document.getElementById('statusChart').getContext('2d');\n");
        scripts.append("new Chart(statusCtx, {\n");
        scripts.append("    type: 'pie',\n");
        scripts.append("    data: {\n");
        scripts.append("        labels: ['✅ Finalized', '⏳ Selected', '❌ Failed'],\n");
        scripts.append("        datasets: [{\n");
        scripts.append("            data: [").append(finalizedCount).append(", ").append(selectedCount).append(", ").append(failedCount).append("],\n");
        scripts.append("            backgroundColor: ['#28a745', '#ffc107', '#dc3545'],\n");
        scripts.append("            borderColor: ['#fff', '#fff', '#fff'],\n");
        scripts.append("            borderWidth: 2\n");
        scripts.append("        }]\n");
        scripts.append("    },\n");
        scripts.append("    options: { responsive: true, plugins: { legend: { position: 'bottom', labels: { font: { size: 12 } } }, title: { display: true, text: 'Borrower Status Breakdown' } } }\n");
        scripts.append("});\n");

        return scripts.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
