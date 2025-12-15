package de.erdbeerbaerlp.dcintegration.common.storage.validation;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates configuration values and provides clear error messages.
 */
public class ConfigValidator {
    
    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^\\d{17,20}$");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+");
    
    /**
     * Validation result with errors and warnings
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(@NotNull String error) {
            errors.add(error);
        }
        
        public void addWarning(@NotNull String warning) {
            warnings.add(warning);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        @NotNull
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        @NotNull
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public void logResults() {
            for (String error : errors) {
                DiscordIntegration.LOGGER.error("[Config Validation] {}", error);
            }
            for (String warning : warnings) {
                DiscordIntegration.LOGGER.warn("[Config Validation] {}", warning);
            }
        }
    }
    
    /**
     * Validates the entire configuration
     * 
     * @param config Configuration to validate
     * @return Validation result
     */
    @NotNull
    public static ValidationResult validate(@NotNull Configuration config) {
        ValidationResult result = new ValidationResult();
        
        validateGeneral(config, result);
        validateChannels(config, result);
        validateEmbedMode(config, result);
        validateLinking(config, result);
        validateRateLimiting(config, result);
        
        return result;
    }
    
    private static void validateGeneral(@NotNull Configuration config, @NotNull ValidationResult result) {
        // Bot token validation
        if (config.general.botToken == null || config.general.botToken.isEmpty() || 
            config.general.botToken.equals("INSERT BOT TOKEN HERE")) {
            result.addError("Bot token is not set! Please set general.botToken in your config.");
        } else if (config.general.botToken.length() < 50) {
            result.addWarning("Bot token appears to be invalid (too short). Discord bot tokens are typically longer.");
        }
        
        // Bot channel validation
        if (config.general.botChannel == null || config.general.botChannel.isEmpty() || 
            config.general.botChannel.equals("000000000")) {
            result.addError("Bot channel is not set! Please set general.botChannel to a valid Discord channel ID.");
        } else if (!isValidDiscordID(config.general.botChannel)) {
            result.addError("Bot channel ID is invalid! Expected 17-20 digit Discord ID, got: " + config.general.botChannel);
        }
        
        // Streaming URL validation
        if (config.general.botStatusType == de.erdbeerbaerlp.dcintegration.common.util.GameType.STREAMING) {
            if (config.general.streamingURL == null || config.general.streamingURL.isEmpty()) {
                result.addError("Streaming URL is required when botStatusType is STREAMING!");
            } else if (!config.general.streamingURL.startsWith("https://twitch.tv/") && 
                      !config.general.streamingURL.startsWith("https://www.youtube.com/watch?v=")) {
                result.addError("Streaming URL must start with https://twitch.tv/ or https://www.youtube.com/watch?v=");
            }
        }
    }
    
    private static void validateChannels(@NotNull Configuration config, @NotNull ValidationResult result) {
        // Validate all channel IDs
        validateChannelID(config.advanced.serverChannelID, "serverChannelID", result);
        validateChannelID(config.advanced.deathsChannelID, "deathsChannelID", result);
        validateChannelID(config.advanced.advancementChannelID, "advancementChannelID", result);
        validateChannelID(config.advanced.chatOutputChannelID, "chatOutputChannelID", result);
        validateChannelID(config.advanced.chatInputChannelID, "chatInputChannelID", result);
        validateChannelID(config.votifier.votifierChannelID, "votifierChannelID", result);
        validateChannelID(config.dynmap.dynmapChannelID, "dynmapChannelID", result);
        validateChannelID(config.commandLog.channelID, "commandLog.channelID", result, true); // Can be "0" to disable
        
        // Validate command log channel
        if (config.commandLog.channelID != null && !config.commandLog.channelID.equals("0") && 
            !config.commandLog.channelID.equals("default") && !isValidDiscordID(config.commandLog.channelID)) {
            result.addError("commandLog.channelID is invalid! Expected Discord channel ID or '0' to disable.");
        }
    }
    
    private static void validateChannelID(String channelID, String fieldName, ValidationResult result) {
        validateChannelID(channelID, fieldName, result, false);
    }
    
    private static void validateChannelID(String channelID, String fieldName, ValidationResult result, boolean allowZero) {
        if (channelID == null || channelID.isEmpty()) {
            result.addWarning(fieldName + " is empty, will use default channel.");
            return;
        }
        
        if (allowZero && channelID.equals("0")) {
            return; // Valid - means disabled
        }
        
        if (channelID.equals("default")) {
            return; // Valid - means use default
        }
        
        if (!isValidDiscordID(channelID)) {
            result.addError(fieldName + " is invalid! Expected Discord channel ID, 'default', or '0' (to disable), got: " + channelID);
        }
    }
    
    private static void validateEmbedMode(@NotNull Configuration config, @NotNull ValidationResult result) {
        if (!config.embedMode.enabled) {
            return; // Skip validation if embed mode is disabled
        }
        
        // Validate embed colors
        validateHexColor(config.embedMode.startMessages.colorHexCode, "embedMode.startMessages.colorHexCode", result);
        validateHexColor(config.embedMode.stopMessages.colorHexCode, "embedMode.stopMessages.colorHexCode", result);
        validateHexColor(config.embedMode.playerJoinMessage.colorHexCode, "embedMode.playerJoinMessage.colorHexCode", result);
        validateHexColor(config.embedMode.playerLeaveMessages.colorHexCode, "embedMode.playerLeaveMessages.colorHexCode", result);
        validateHexColor(config.embedMode.deathMessage.colorHexCode, "embedMode.deathMessage.colorHexCode", result);
        validateHexColor(config.embedMode.advancementMessage.colorHexCode, "embedMode.advancementMessage.colorHexCode", result);
        validateHexColor(config.embedMode.chatMessages.colorHexCode, "embedMode.chatMessages.colorHexCode", result);
        
        // Validate custom JSON if set
        if (config.embedMode.startMessages.customJSON != null && !config.embedMode.startMessages.customJSON.isEmpty()) {
            validateJSON(config.embedMode.startMessages.customJSON, "embedMode.startMessages.customJSON", result);
        }
    }
    
    private static void validateHexColor(String color, String fieldName, ValidationResult result) {
        if (color == null || color.isEmpty()) {
            result.addError(fieldName + " is empty! Expected hex color code (e.g., #FF0000).");
            return;
        }
        
        if (!HEX_COLOR_PATTERN.matcher(color).matches()) {
            result.addError(fieldName + " is invalid! Expected hex color code (e.g., #FF0000), got: " + color);
        }
    }
    
    private static void validateJSON(String json, String fieldName, ValidationResult result) {
        try {
            // Basic JSON validation - check if it's valid JSON structure
            if (!json.trim().startsWith("{") || !json.trim().endsWith("}")) {
                result.addError(fieldName + " does not appear to be valid JSON (must start with { and end with }).");
            }
        } catch (Exception e) {
            result.addWarning(fieldName + " may contain invalid JSON: " + e.getMessage());
        }
    }
    
    private static void validateLinking(@NotNull Configuration config, @NotNull ValidationResult result) {
        if (!config.linking.enableLinking) {
            return; // Skip if linking is disabled
        }
        
        // Validate linked role ID
        if (config.linking.linkedRoleID != null && !config.linking.linkedRoleID.equals("0") && 
            !isValidDiscordID(config.linking.linkedRoleID)) {
            result.addError("linking.linkedRoleID is invalid! Expected Discord role ID or '0' to disable.");
        }
        
        // Validate required roles
        if (config.linking.requiredRoles != null) {
            for (int i = 0; i < config.linking.requiredRoles.length; i++) {
                String roleID = config.linking.requiredRoles[i];
                if (!isValidDiscordID(roleID)) {
                    result.addError("linking.requiredRoles[" + i + "] is invalid! Expected Discord role ID, got: " + roleID);
                }
            }
        }
        
        // Validate admin roles
        if (config.commands.adminRoleIDs != null) {
            for (int i = 0; i < config.commands.adminRoleIDs.length; i++) {
                String roleID = config.commands.adminRoleIDs[i];
                if (!isValidDiscordID(roleID)) {
                    result.addError("commands.adminRoleIDs[" + i + "] is invalid! Expected Discord role ID, got: " + roleID);
                }
            }
        }
    }
    
    private static void validateRateLimiting(@NotNull Configuration config, @NotNull ValidationResult result) {
        if (!config.rateLimiting.enabled) {
            return; // Skip if disabled
        }
        
        if (config.rateLimiting.maxRequestsPerSecond <= 0 || config.rateLimiting.maxRequestsPerSecond > 100) {
            result.addWarning("rateLimiting.maxRequestsPerSecond should be between 1 and 100. Discord's limit is 50.");
        }
        
        if (config.rateLimiting.maxBackoffMs < 0) {
            result.addError("rateLimiting.maxBackoffMs cannot be negative!");
        }
        
        if (config.rateLimiting.maxBatchSize <= 0) {
            result.addError("rateLimiting.maxBatchSize must be greater than 0!");
        }
        
        if (config.rateLimiting.processingIntervalMs < 10) {
            result.addWarning("rateLimiting.processingIntervalMs is very low (" + config.rateLimiting.processingIntervalMs + "ms). This may cause high CPU usage.");
        }
        
        if (config.rateLimiting.maxRetries < 0) {
            result.addError("rateLimiting.maxRetries cannot be negative!");
        }
    }
    
    /**
     * Validates if a string is a valid Discord ID (17-20 digits)
     */
    private static boolean isValidDiscordID(@NotNull String id) {
        return DISCORD_ID_PATTERN.matcher(id).matches();
    }
    
    /**
     * Validates if a string is a valid URL
     */
    private static boolean isValidURL(@NotNull String url) {
        return URL_PATTERN.matcher(url).matches();
    }
}

