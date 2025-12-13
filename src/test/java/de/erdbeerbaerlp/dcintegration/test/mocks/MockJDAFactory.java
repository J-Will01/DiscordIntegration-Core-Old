package de.erdbeerbaerlp.dcintegration.test.mocks;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

/**
 * Factory for creating mocked JDA instances for testing.
 * Provides verification methods to check Discord messages.
 */
public class MockJDAFactory {
    
    /**
     * Represents a message sent to Discord
     */
    public static class SentDiscordMessage {
        public final String channelID;
        public final MessageCreateData messageData;
        public final String content;
        public final MessageEmbed embed;
        public final long timestamp;
        
        public SentDiscordMessage(String channelID, MessageCreateData messageData) {
            this.channelID = channelID;
            this.messageData = messageData;
            this.content = messageData.getContent();
            this.embed = messageData.getEmbeds().isEmpty() ? null : messageData.getEmbeds().get(0);
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    private final Map<String, List<SentDiscordMessage>> sentMessages = new HashMap<>();
    private final Map<String, TextChannel> channels = new HashMap<>();
    private JDA mockJDA;
    private Guild mockGuild;
    private net.dv8tion.jda.api.entities.SelfUser mockSelfUser;
    private Member mockSelfMember;
    
    /**
     * Creates a fully configured mock JDA instance
     */
    public JDA createMockJDA() {
        mockJDA = Mockito.mock(JDA.class);
        mockGuild = Mockito.mock(Guild.class);
        mockSelfUser = Mockito.mock(net.dv8tion.jda.api.entities.SelfUser.class);
        mockSelfMember = Mockito.mock(Member.class);
        
        // Setup basic JDA mocks
        when(mockJDA.getSelfUser()).thenReturn(mockSelfUser);
        when(mockJDA.getGuilds()).thenReturn(List.of(mockGuild));
        when(mockSelfUser.getIdLong()).thenReturn(123456789L);
        when(mockSelfUser.getId()).thenReturn("123456789");
        when(mockGuild.getMember(mockSelfUser)).thenReturn(mockSelfMember);
        when(mockGuild.getMemberById(anyLong())).thenReturn(mockSelfMember);
        
        // Mock awaitReady to return immediately
        try {
            when(mockJDA.awaitReady()).thenReturn(mockJDA);
        } catch (InterruptedException e) {
            // Should not happen in tests
        }
        
        return mockJDA;
    }
    
    /**
     * Creates a mock text channel with the given ID
     */
    public TextChannel createMockTextChannel(String channelID) {
        if (channels.containsKey(channelID)) {
            return channels.get(channelID);
        }
        
        // Create as TextChannel (which extends StandardGuildMessageChannel)
        TextChannel channel = Mockito.mock(TextChannel.class);
        when(channel.getId()).thenReturn(channelID);
        when(channel.getIdLong()).thenReturn(Long.parseLong(channelID));
        when(channel.getGuild()).thenReturn(mockGuild);
        when(channel.getName()).thenReturn("test-channel");
        
        // Mock sendMessage to return MessageCreateAction, then mock submit() to capture messages
        // JDA's channel.sendMessage() returns MessageCreateAction, not just RestAction
        when(channel.sendMessage(any(MessageCreateData.class))).thenAnswer((Answer<MessageCreateAction>) invocation -> {
            // Capture the MessageCreateData from the sendMessage call
            final MessageCreateData data = invocation.getArgument(0);
            
            // Create a mock MessageCreateAction that will capture the message when submit() is called
            @SuppressWarnings("unchecked")
            MessageCreateAction messageCreateAction = Mockito.mock(MessageCreateAction.class);
            
            // Mock submit() to capture the message and return CompletableFuture
            when(messageCreateAction.submit()).thenAnswer((Answer<CompletableFuture<Message>>) submitInvocation -> {
                // Capture the message when submit() is called
                sentMessages.computeIfAbsent(channelID, k -> new ArrayList<>()).add(new SentDiscordMessage(channelID, data));
                
                // Create a mock message
                Message mockMessage = Mockito.mock(Message.class);
                when(mockMessage.getIdLong()).thenReturn(System.currentTimeMillis());
                when(mockMessage.getId()).thenReturn(String.valueOf(System.currentTimeMillis()));
                
                CompletableFuture<Message> future = new CompletableFuture<>();
                future.complete(mockMessage);
                return future;
            });
            
            return messageCreateAction;
        });
        
        // Mock getTextChannelById - JDA's getTextChannelById accepts String
        // But we also need to mock the long version in case it's called
        when(mockJDA.getTextChannelById(channelID)).thenReturn(channel);
        try {
            long channelIdLong = Long.parseLong(channelID);
            when(mockJDA.getTextChannelById(channelIdLong)).thenReturn(channel);
        } catch (NumberFormatException e) {
            // Channel ID is not a valid long, skip long mock
        }
        
        channels.put(channelID, channel);
        return channel;
    }
    
    /**
     * Gets the mock JDA instance
     */
    public JDA getMockJDA() {
        return mockJDA;
    }
    
    /**
     * Gets the mock guild
     */
    public Guild getMockGuild() {
        return mockGuild;
    }
    
    /**
     * Gets the mock self user
     */
    public User getMockSelfUser() {
        return mockSelfUser;
    }
    
    /**
     * Gets all messages sent to a specific channel
     */
    public List<SentDiscordMessage> getSentMessages(String channelID) {
        return new ArrayList<>(sentMessages.getOrDefault(channelID, new ArrayList<>()));
    }
    
    /**
     * Gets all sent messages across all channels
     */
    public List<SentDiscordMessage> getAllSentMessages() {
        List<SentDiscordMessage> all = new ArrayList<>();
        for (List<SentDiscordMessage> messages : sentMessages.values()) {
            all.addAll(messages);
        }
        return all;
    }
    
    /**
     * Clears all sent messages
     */
    public void clearMessages() {
        sentMessages.clear();
    }
    
    /**
     * Gets the number of messages sent to a channel
     */
    public int getMessageCount(String channelID) {
        return sentMessages.getOrDefault(channelID, new ArrayList<>()).size();
    }
    
    /**
     * Gets the total number of messages sent
     */
    public int getTotalMessageCount() {
        return getAllSentMessages().size();
    }
    
    /**
     * Asserts that a message with the given content was sent to the channel
     */
    public void assertMessageSent(String channelID, String content) {
        List<SentDiscordMessage> messages = getSentMessages(channelID);
        for (SentDiscordMessage msg : messages) {
            if (msg.content != null && msg.content.contains(content)) {
                return;
            }
        }
        throw new AssertionError("No message containing '" + content + "' was sent to channel " + channelID);
    }
    
    /**
     * Asserts that an embed with the given title was sent to the channel
     */
    public void assertEmbedTitle(String channelID, String title) {
        List<SentDiscordMessage> messages = getSentMessages(channelID);
        for (SentDiscordMessage msg : messages) {
            if (msg.embed != null && title.equals(msg.embed.getTitle())) {
                return;
            }
        }
        throw new AssertionError("No embed with title '" + title + "' was sent to channel " + channelID);
    }
    
    /**
     * Asserts that an embed with the given description was sent to the channel
     */
    public void assertEmbedDescription(String channelID, String description) {
        List<SentDiscordMessage> messages = getSentMessages(channelID);
        for (SentDiscordMessage msg : messages) {
            if (msg.embed != null && description.equals(msg.embed.getDescription())) {
                return;
            }
        }
        throw new AssertionError("No embed with description '" + description + "' was sent to channel " + channelID);
    }
    
    /**
     * Gets the last message sent to a channel
     */
    public SentDiscordMessage getLastMessage(String channelID) {
        List<SentDiscordMessage> messages = getSentMessages(channelID);
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }
    
    /**
     * Gets a mock channel by ID, creating it if it doesn't exist
     */
    public TextChannel getChannel(String channelID) {
        if (channels.containsKey(channelID)) {
            return channels.get(channelID);
        }
        return createMockTextChannel(channelID);
    }
}

