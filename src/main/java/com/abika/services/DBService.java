package com.abika.services;

import com.abika.model.Borrower;
import com.abika.utils.ConfigReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class DBService {
    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    private static final Logger logger = LoggerFactory.getLogger(DBService.class);
    private static HikariDataSource dataSource;
    private static final Object lock = new Object();

    public DBService() {
        loadConfig();
        initializeConnectionPool();
    }

    private void loadConfig() {
        dbUrl = ConfigReader.get("db.url");
        dbUser = ConfigReader.get("db.username");
        dbPassword = ConfigReader.get("db.password");
        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IllegalStateException("db.url is missing");
        }
        // Defensive compatibility for MySQL 8/9 caching_sha2_password on local development.
        if (dbUrl.startsWith("jdbc:mysql:") && !dbUrl.contains("allowPublicKeyRetrieval=")) {
            dbUrl += (dbUrl.contains("?") ? "&" : "?") + "allowPublicKeyRetrieval=true";
        }
    }

    private void initializeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) return;
        synchronized (lock) {
            if (dataSource != null && !dataSource.isClosed()) return;
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(dbUrl);
                config.setUsername(dbUser);
                config.setPassword(dbPassword);
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(10000);
                config.setValidationTimeout(3000);
                config.setIdleTimeout(300000);
                config.setMaxLifetime(1200000);
                config.setKeepaliveTime(120000);
                config.setAutoCommit(true);
                dataSource = new HikariDataSource(config);
                logger.info("✅ HikariCP connection pool initialized. Max pool size: 10, Min idle: 2");
            } catch (Exception e) {
                logger.error("❌ Failed to initialize HikariCP connection pool", e);
                throw new IllegalStateException("Failed to initialize database connection pool", e);
            }
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) initializeConnectionPool();
        return dataSource.getConnection();
    }

    public static void closeConnectionPool() {
        synchronized (lock) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                logger.info("✅ HikariCP connection pool closed");
            }
            dataSource = null;
        }
    }

    public List<Map<String, Object>> executeSelect(String query, List<Object> params) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            setParameters(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    resultList.add(row);
                }
            }
            return resultList;
        } catch (SQLException e) {
            logger.error("Database read failed; refusing to continue with an empty safety dataset", e);
            throw new IllegalStateException("Database read failed", e);
        }
    }

    public int executeUpdate(String query, List<Object> params) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            setParameters(pstmt, params);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Database update failed", e);
            throw new IllegalStateException("Database update failed", e);
        }
    }

    private void setParameters(PreparedStatement pstmt, List<Object> params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) pstmt.setObject(i + 1, params.get(i));
    }

    public List<String> getNPABorrowers() {
        String sql = "SELECT particular FROM manual_lending_investment WHERE isnpa=?";
        List<Map<String, Object>> result = executeSelect(sql, List.of(true));
        List<String> borrowers = new ArrayList<>();
        for (Map<String, Object> row : result) {
            Object value = row.get("particular");
            if (value != null) borrowers.add(value.toString().trim());
        }
        return borrowers;
    }

    public Set<String> getNPABorrowersAsSet() {
        long start = System.currentTimeMillis();
        Set<String> borrowers = new HashSet<>();

        List<Map<String, Object>> result1 = executeSelect(
                "SELECT DISTINCT particular FROM manual_lending_investment WHERE isnpa=? AND particular IS NOT NULL",
                List.of(true));
        for (Map<String, Object> row : result1) {
            Object value = row.get("particular");
            if (value != null) borrowers.add(value.toString().trim());
        }

        List<Map<String, Object>> result2 = executeSelect(
                "SELECT DISTINCT name FROM default_borrowers WHERE name IS NOT NULL", List.of());
        for (Map<String, Object> row : result2) {
            Object value = row.get("name");
            if (value != null && !value.toString().isEmpty()) borrowers.add(value.toString().trim());
        }

        logger.info("✅ Batch loaded {} NPA borrowers into HashSet in {}ms",
                borrowers.size(), System.currentTimeMillis() - start);
        return borrowers;
    }

    public List<String> getNonNPABorrowers() {
        String sql = "SELECT particular FROM manual_lending_investment " +
                "WHERE investment_status = ? AND loan_status = ? " +
                "GROUP BY particular HAVING MIN(isnpa) = ? AND MAX(isnpa) = ?";
        List<Map<String, Object>> result = executeSelect(
                sql, List.of("SOLD", "CLOSED", false, false));
        List<String> borrowers = new ArrayList<>();
        for (Map<String, Object> row : result) {
            Object value = row.get("particular");
            if (value != null) borrowers.add(value.toString().trim());
        }
        logger.info("📊 getNonNPABorrowers query returned {} borrowers", borrowers.size());
        return borrowers;
    }

    public void storeBorrowerList(List<Borrower> borrowerList, String mobileNumber) {
        if (borrowerList == null || borrowerList.isEmpty()) return;
        String sql = "INSERT INTO borrower_loans " +
                "(loanId, creditScore, lendenScore, income, loanAmount, borrowerType, " +
                "interestRate, name, age, lendingAmount, tenure, user) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Borrower b : borrowerList) {
                stmt.setString(1, b.getLoanId());
                stmt.setDouble(2, b.getCreditScore());
                stmt.setDouble(3, b.getLendenScore());
                stmt.setDouble(4, b.getIncome());
                stmt.setDouble(5, b.getLoanAmount());
                stmt.setString(6, b.getBorrowerType());
                stmt.setDouble(7, b.getInterestRate());
                stmt.setString(8, b.getName());
                stmt.setInt(9, b.getAge());
                stmt.setDouble(10, b.getLendingAmount());
                stmt.setInt(11, b.getTenure());
                stmt.setString(12, mobileNumber);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            logger.error("Error in storeBorrowerList", e);
            throw new IllegalStateException("Failed to store borrower list", e);
        }
    }

    public void storeTrustedBorrowerList(List<Borrower> borrowerList) {
        if (borrowerList == null || borrowerList.isEmpty()) return;
        String sql = "INSERT INTO trusted_borrower " +
                "(loanId, creditScore, lendenScore, income, loanAmount, borrowerType, " +
                "interestRate, name, age, lendingAmount, tenure) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Borrower b : borrowerList) {
                stmt.setString(1, b.getLoanId());
                stmt.setDouble(2, b.getCreditScore());
                stmt.setDouble(3, b.getLendenScore());
                stmt.setDouble(4, b.getIncome());
                stmt.setDouble(5, b.getLoanAmount());
                stmt.setString(6, b.getBorrowerType());
                stmt.setDouble(7, b.getInterestRate());
                stmt.setString(8, b.getName());
                stmt.setInt(9, b.getAge());
                stmt.setDouble(10, b.getLendingAmount());
                stmt.setInt(11, b.getTenure());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            logger.error("Error in storeTrustedBorrowerList", e);
            throw new IllegalStateException("Failed to store trusted borrower list", e);
        }
    }

    public List<String> getNPABorrowerNames() {
        List<Map<String, Object>> result = executeSelect(
                "SELECT DISTINCT name FROM default_borrowers WHERE name IS NOT NULL ORDER BY name",
                List.of());
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : result) {
            Object value = row.get("name");
            if (value != null && !value.toString().isEmpty()) names.add(value.toString().trim());
        }
        return names;
    }

    public boolean isTrustedBorrower(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String sql = """
        SELECT 1
        FROM trusted_borrower tb
        WHERE LOWER(tb.name) = ?
          AND NOT EXISTS (
              SELECT 1 FROM manual_lending_investment mli
              WHERE LOWER(mli.particular) = LOWER(tb.name) AND mli.isnpa = true
          )
        LIMIT 1
        """;
        return !executeSelect(sql, List.of(name.trim().toLowerCase())).isEmpty();
    }

    public Set<String> getTrustedBorrowersAsSet() {
        long start = System.currentTimeMillis();
        Set<String> trustedBorrowers = new HashSet<>();
        String sql = """
        SELECT DISTINCT LOWER(tb.name) as name
        FROM trusted_borrower tb
        WHERE NOT EXISTS (
            SELECT 1 FROM manual_lending_investment mli
            WHERE LOWER(mli.particular) = LOWER(tb.name) AND mli.isnpa = true
        )
        """;
        for (Map<String, Object> row : executeSelect(sql, List.of())) {
            Object value = row.get("name");
            if (value != null && !value.toString().isEmpty()) {
                trustedBorrowers.add(value.toString().trim());
            }
        }
        logger.info("✅ Batch loaded {} trusted borrowers into HashSet in {}ms",
                trustedBorrowers.size(), System.currentTimeMillis() - start);
        return trustedBorrowers;
    }

    public List<String> getCurrentlyLendedBorrowers(String user) {
        if (user == null || user.trim().isEmpty()) {
            throw new IllegalArgumentException("User parameter is null or empty");
        }
        String sql = "SELECT particular FROM manual_lending_investment " +
                "WHERE isnpa = ? AND investment_status = ? AND loan_status = ? AND created_by = ? " +
                "ORDER BY particular";
        List<Map<String, Object>> result = executeSelect(
                sql, List.of(false, "NOT_SOLD", "ACTIVE", user.trim()));
        List<String> borrowers = new ArrayList<>();
        for (Map<String, Object> row : result) {
            Object value = row.get("particular");
            if (value != null && !value.toString().isEmpty()) borrowers.add(value.toString().trim());
        }
        return borrowers;
    }
}
