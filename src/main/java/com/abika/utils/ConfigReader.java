package com.abika.utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConfigReader {
    private static final Properties properties = new Properties();
    private static final Logger logger = LoggerFactory.getLogger(ConfigReader.class);

    static {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.error("config.properties file not found in classpath");
            } else {
                properties.load(input);
                logger.info("Loaded {} properties from config.properties", properties.size());
            }
        } catch (IOException ex) {
            logger.error("Error while reading config.properties", ex);
        }
    }

    /**
     * Get configuration value by key. Supports environment variable substitution.
     * @param key the property key
     * @return the property value or null if not found
     */
    public static String get(String key) {
        if (key == null || key.trim().isEmpty()) {
            logger.warn("Config key is null or empty");
            return null;
        }
        String value = properties.getProperty(key.trim());
        if (value == null) {
            logger.warn("Configuration key not found: {}", key);
            return null;
        }
        return resolve(value);
    }

    /**
     * Set configuration value at runtime
     * @param key the property key
     * @param value the property value
     */
    public static void set(String key, String value) {
        if (key != null && !key.trim().isEmpty()) {
            properties.setProperty(key.trim(), value);
        }
    }

    /**
     * Get list of rule names from rule.names property
     * Example: rule.names=rule2,rule5 returns ["Repeated Lenders - Medium Risk", "Trusted Lenders - Medium Risk"]
     * @return list of rule names
     */
    public static List<String> getRuleNames() {
        String ruleRefs = get("rule.names");
        List<String> rules = new ArrayList<>();

        if (ruleRefs == null || ruleRefs.isEmpty()) {
            return rules;
        }

        for (String ref : ruleRefs.split(",")) {
            String ruleName = get(ref.trim());
            if (ruleName != null && !ruleName.isEmpty()) {
                rules.add(ruleName);
            }
        }
        return rules;
    }

    /**
     * Resolve environment variables in the format ${ENV_VAR}
     */
    private static String resolve(String value) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }

        String envKey = value.substring(2, value.length() - 1);
        try {
            Dotenv dotenv = Dotenv.load();
            String envValue = dotenv.get(envKey);
            if (envValue != null && !envValue.isEmpty()) {
                return envValue;
            }
            logger.warn("Environment variable not found: {}", envKey);
        } catch (Exception e) {
            logger.warn("Error resolving environment variable {}: {}", envKey, e.getMessage());
        }

        return value; // return as-is if not resolved
    }
}

