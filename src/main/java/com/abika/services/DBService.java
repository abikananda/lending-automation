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
    }

    /**
     * Initialize HikariCP connection pool (thread-safe singleton)
     * Reuses connections instead of creating new ones per query
     */
    private void initializeConnectionPool() {
        if (dataSource != null) return;

        synchronized (lock) {
            if (dataSource != null) return;

            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(dbUrl);
                config.setUsername(dbUser);
                config.setPassword(dbPassword);
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);
                config.setAutoCommit(true);

                dataSource = new HikariDataSource(config);
                logger.info("✅ HikariCP connection pool initialized. Max pool size: 10, Min idle: 2");
            } catch (Exception e) {
                logger.error("❌ Failed to initialize HikariCP connection pool", e);
                throw new RuntimeException("Failed to initialize connection pool", e);
            }
        }
    }

    /**
     * Get connection from HikariCP pool instead of creating new one
     * Reduces connection overhead from ~10-50ms per query to near-zero
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Connection pool not initialized");
        }
        return dataSource.getConnection();
    }

    /**
     * Close the connection pool gracefully (call on shutdown)
     */
    public static void closeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("✅ HikariCP connection pool closed");
        }
    }
    public List<Map<String, Object>> executeSelect(String query, List<Object> params) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            setParameters(pstmt, params);
            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                resultList.add(row);
            }
        } catch (SQLException e) {
            logger.error("Error in executeSelect", e);
        }
        return resultList;
    }
    public int executeUpdate(String query, List<Object> params) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            setParameters(pstmt, params);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error in executeUpdate", e);
        }
        return 0;
    }
    private void setParameters(PreparedStatement pstmt, List<Object> params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) {
            pstmt.setObject(i + 1, params.get(i));
        }
    }
    public List<String> getNPABorrowers() {
        String sql = "SELECT particular FROM manual_lending_investment WHERE isnpa=?";
        List<Map<String, Object>> result = executeSelect(sql, List.of(true));
        List<String> borrowers = new ArrayList<>();
        for (Map<String, Object> row : result) {
            Object value = row.get("particular");
            if (value != null) {
                borrowers.add(value.toString().trim());
            }
        }
        return borrowers;
    }

    /**
     * Load all NPA borrowers into a HashSet for O(1) lookup
     * Consolidates getNPABorrowers() and getNPABorrowerNames() into a single batch operation
     * @return HashSet of NPA borrower names for fast membership testing
     */
    public Set<String> getNPABorrowersAsSet() {
        long startTime = System.currentTimeMillis();
        Set<String> borrowers = new HashSet<>();

        // Get NPA borrowers from manual_lending_investment table
        String sql1 = "SELECT DISTINCT particular FROM manual_lending_investment WHERE isnpa=? AND particular IS NOT NULL";
        List<Map<String, Object>> result1 = executeSelect(sql1, List.of(true));
        for (Map<String, Object> row : result1) {
            Object value = row.get("particular");
            if (value != null) {
                borrowers.add(value.toString().trim());
            }
        }

        // Get NPA borrower names from default_borrowers table
        String sql2 = "SELECT DISTINCT name FROM default_borrowers WHERE name IS NOT NULL";
        List<Map<String, Object>> result2 = executeSelect(sql2, List.of());
        for (Map<String, Object> row : result2) {
            Object value = row.get("name");
            if (value != null && !value.toString().isEmpty()) {
                borrowers.add(value.toString().trim());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("✅ Batch loaded {} NPA borrowers into HashSet in {}ms", borrowers.size(), duration);
        return borrowers;
    }

    public List<String> getNonNPABorrowers() {
        String sql = "SELECT particular FROM manual_lending_investment " +
                "WHERE investment_status = ? AND loan_status = ? " +
                "GROUP BY particular " +
                "HAVING MIN(isnpa) = ? AND MAX(isnpa) = ?";
        List<Map<String, Object>> result = executeSelect(
                sql,
                List.of("SOLD", "CLOSED", false, false)
        );
        List<String> borrowers = new ArrayList<>();
        for (Map<String, Object> row : result) {
            Object value = row.get("particular");
            if (value != null) {
                borrowers.add(value.toString().trim());
            }
        }
        logger.info("📊 getNonNPABorrowers query returned {} borrowers", borrowers.size());
        if (borrowers.isEmpty()) {
            logger.warn("⚠️ WARNING: getNonNPABorrowers returned empty. Query criteria: investment_status='SOLD', loan_status='CLOSED', isnpa=false");
            logger.warn("   Consider checking: 1) DB data exists, 2) Query criteria are too restrictive");
        }
        return borrowers;
    }
    public void storeBorrowerList(List<Borrower> borrowerList, String mobileNumber) {
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
            throw new RuntimeException("Failed to store borrower list", e);
        }
    }
    public void storeTrustedBorrowerList(List<Borrower> borrowerList) {
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
            throw new RuntimeException("Failed to store trusted borrower list", e);
        }
    }
    public List<String> getNPABorrowerNames() {
        String sql = "SELECT DISTINCT name FROM default_borrowers WHERE name IS NOT NULL ORDER BY name";
        List<Map<String, Object>> result = executeSelect(sql, List.of());
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : result) {
            Object value = row.get("name");
            if (value != null && !value.toString().isEmpty()) {
                names.add(value.toString().trim());
            }
        }
        return names;
    }
    public boolean isTrustedBorrower(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String sql = """
        SELECT 1
        FROM trusted_borrower tb
        WHERE LOWER(tb.name) = ?
          AND NOT EXISTS (
              SELECT 1
              FROM manual_lending_investment mli
              WHERE LOWER(mli.particular) = LOWER(tb.name)
                AND mli.isnpa = true
          )
        LIMIT 1
        """;
        List<Map<String, Object>> result = executeSelect(sql, List.of(name.trim().toLowerCase()));
        return !result.isEmpty();
    }

    /**
     * Load all trusted borrowers into a HashSet for O(1) lookup
     * Replaces individual isTrustedBorrower() calls for massive performance improvement (O(N) vs O(N²))
     * @return HashSet of trusted borrower names for fast membership testing
     */
    public Set<String> getTrustedBorrowersAsSet() {
        long startTime = System.currentTimeMillis();
        Set<String> trustedBorrowers = new HashSet<>();

        String sql = """
        SELECT DISTINCT LOWER(tb.name) as name
        FROM trusted_borrower tb
        WHERE NOT EXISTS (
            SELECT 1
            FROM manual_lending_investment mli
            WHERE LOWER(mli.particular) = LOWER(tb.name)
              AND mli.isnpa = true
        )
        """;

        List<Map<String, Object>> result = executeSelect(sql, List.of());
        for (Map<String, Object> row : result) {
            Object value = row.get("name");
            if (value != null && !value.toString().isEmpty()) {
                trustedBorrowers.add(value.toString().trim());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("✅ Batch loaded {} trusted borrowers into HashSet in {}ms", trustedBorrowers.size(), duration);
        return trustedBorrowers;
    }

    public List<String> getCurrentlyLendedBorrowers(String user) {
        if (user == null || user.trim().isEmpty()) {
            logger.warn("User parameter is null or empty");
            return List.of();
        }
        String sql = "SELECT particular FROM manual_lending_investment " +
                "WHERE isnpa = ? " +
                "AND investment_status = ? " +
                "AND loan_status = ? " +
                "AND created_by = ? " +
                "ORDER BY particular";

        List<Object> params = List.of(false, "NOT_SOLD", "ACTIVE", user.trim());
        List<Map<String, Object>> result = executeSelect(sql, params);
        List<String> borrowers = new ArrayList<>();
        for (Map<String, Object> row : result) {
            Object value = row.get("particular");
            if (value != null && !value.toString().isEmpty()) {
                borrowers.add(value.toString().trim());
            }
        }
        return borrowers;
    }
}

