package de.erdbeerbaerlp.dcintegration.test;

import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.validation.ConfigValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for configuration validation
 */
public class ConfigValidationTests {
    private Configuration config;
    
    @BeforeEach
    public void setUp() {
        config = new Configuration();
    }
    
    @Test
    public void testValidConfiguration() {
        config.general.botToken = "TEST_TOKEN_FOR_VALIDATION_PLACEHOLDER_DO_NOT_USE_IN_PRODUCTION_123456789";
        config.general.botChannel = "123456789012345678";
        
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        Assertions.assertFalse(result.hasErrors());
    }
    
    @Test
    public void testMissingBotToken() {
        config.general.botToken = "INSERT BOT TOKEN HERE";
        config.general.botChannel = "123456789012345678";
        
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        Assertions.assertTrue(result.hasErrors());
        Assertions.assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("bot") && e.toLowerCase().contains("token")));
    }
    
    @Test
    public void testInvalidChannelID() {
        config.general.botToken = "TEST_TOKEN_FOR_VALIDATION_PLACEHOLDER_DO_NOT_USE_IN_PRODUCTION_123456789";
        config.general.botChannel = "invalid";
        
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        Assertions.assertTrue(result.hasErrors());
    }
    
    @Test
    public void testInvalidHexColor() {
        config.general.botToken = "TEST_TOKEN_FOR_VALIDATION_PLACEHOLDER_DO_NOT_USE_IN_PRODUCTION_123456789";
        config.general.botChannel = "123456789012345678";
        config.embedMode.enabled = true;
        config.embedMode.startMessages.colorHexCode = "invalid";
        
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        Assertions.assertTrue(result.hasErrors());
    }
    
    @Test
    public void testValidHexColor() {
        config.general.botToken = "TEST_TOKEN_FOR_VALIDATION_PLACEHOLDER_DO_NOT_USE_IN_PRODUCTION_123456789";
        config.general.botChannel = "123456789012345678";
        config.embedMode.enabled = true;
        config.embedMode.startMessages.colorHexCode = "#FF0000";
        
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        Assertions.assertFalse(result.hasErrors());
    }
    
    @Test
    public void testInvalidRoleID() {
        config.general.botToken = "TEST_TOKEN_FOR_VALIDATION_PLACEHOLDER_DO_NOT_USE_IN_PRODUCTION_123456789";
        config.general.botChannel = "123456789012345678";
        config.linking.enableLinking = true;
        config.linking.linkedRoleID = "invalid";
        
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        Assertions.assertTrue(result.hasErrors());
    }
    
    @Test
    public void testStreamingURLValidation() {
        config.general.botToken = "TEST_TOKEN_FOR_VALIDATION_PLACEHOLDER_DO_NOT_USE_IN_PRODUCTION_123456789";
        config.general.botChannel = "123456789012345678";
        config.general.botStatusType = de.erdbeerbaerlp.dcintegration.common.util.GameType.STREAMING;
        config.general.streamingURL = "";
        
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        Assertions.assertTrue(result.hasErrors());
    }
    
    @Test
    public void testRateLimitingValidation() {
        config.general.botToken = "TEST_TOKEN_FOR_VALIDATION_PLACEHOLDER_DO_NOT_USE_IN_PRODUCTION_123456789";
        config.general.botChannel = "123456789012345678";
        config.rateLimiting.enabled = true;
        config.rateLimiting.maxBatchSize = -1;
        
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        Assertions.assertTrue(result.hasErrors());
    }
}

