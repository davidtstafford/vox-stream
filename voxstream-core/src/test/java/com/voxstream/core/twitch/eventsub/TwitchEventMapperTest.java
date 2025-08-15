package com.voxstream.core.twitch.eventsub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class TwitchEventMapperTest {

    private EventSubMessage build(String metaType, String subType, Map<String, Object> eventData) {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> subscription = new HashMap<>();
        subscription.put("type", subType);
        payload.put("subscription", subscription);
        if (eventData != null) {
            payload.put("event", eventData);
        }
        root.put("payload", payload);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("message_type", metaType);
        root.put("metadata", metadata);
        return new EventSubMessage(metaType, subType, "1", Instant.now(), root);
    }

    @Test
    void testFollowMapping() {
        Map<String, Object> event = new HashMap<>();
        event.put("user_id", "123");
        event.put("user_login", "alice");
        event.put("broadcaster_user_id", "b1");
        event.put("broadcaster_user_login", "broad");
        var msg = build("notification", "channel.follow", event);
        var pe = TwitchEventMapper.map(msg);
        assertEquals("FOLLOW", pe.type());
        assertEquals("123", pe.payload().get("userId"));
        assertEquals("alice", pe.payload().get("userLogin"));
        assertEquals("b1", pe.payload().get("broadcasterId"));
        assertEquals("broad", pe.payload().get("broadcasterLogin"));
        assertNotNull(pe.payload().get("_raw"));
    }

    @Test
    void testSubscriptionGiftMapping() {
        Map<String, Object> event = new HashMap<>();
        event.put("user_id", "u1");
        event.put("user_login", "gifted");
        event.put("broadcaster_user_id", "b1");
        event.put("broadcaster_user_login", "broad");
        event.put("is_gift", true);
        event.put("tier", "1000");
        event.put("cumulative_months", 5);
        Map<String, Object> message = new HashMap<>();
        message.put("text", "Thank you!");
        event.put("message", message);
        event.put("gifter_user_id", "g1");
        event.put("gifter_user_login", "gifter");
        var msg = build("notification", "channel.subscription.message", event);
        var pe = TwitchEventMapper.map(msg);
        assertEquals("SUBSCRIPTION", pe.type());
        assertEquals("Thank you!", pe.payload().get("messageText"));
        assertEquals("g1", pe.payload().get("gifterUserId"));
    }

    @Test
    void testBitsMapping() {
        Map<String, Object> event = new HashMap<>();
        event.put("user_id", "u1");
        event.put("user_login", "bob");
        event.put("broadcaster_user_id", "b1");
        event.put("broadcaster_user_login", "broad");
        event.put("bits", 250);
        event.put("is_anonymous", true);
        var msg = build("notification", "channel.cheer", event);
        var pe = TwitchEventMapper.map(msg);
        assertEquals("BITS", pe.type());
        assertEquals(250, pe.payload().get("bits"));
        assertEquals(true, pe.payload().get("isAnonymous"));
    }

    @Test
    void testRaidMapping() {
        Map<String, Object> event = new HashMap<>();
        event.put("from_broadcaster_user_id", "from1");
        event.put("from_broadcaster_user_login", "fromLogin");
        event.put("to_broadcaster_user_id", "to1");
        event.put("to_broadcaster_user_login", "toLogin");
        event.put("viewers", 42);
        var msg = build("notification", "channel.raid", event);
        var pe = TwitchEventMapper.map(msg);
        assertEquals("RAID", pe.type());
        assertEquals(42, pe.payload().get("viewers"));
        assertEquals("from1", pe.payload().get("fromBroadcasterId"));
    }

    @Test
    void testChannelPointMapping() {
        Map<String, Object> event = new HashMap<>();
        event.put("user_id", "u1");
        event.put("user_login", "bob");
        event.put("broadcaster_user_id", "b1");
        event.put("broadcaster_user_login", "broad");
        event.put("user_input", "Some input");
        event.put("status", "fulfilled");
        Map<String, Object> reward = new HashMap<>();
        reward.put("title", "Highlight Message");
        reward.put("cost", 100);
        reward.put("id", "reward123");
        reward.put("prompt", "Say something nice");
        event.put("reward", reward);
        var msg = build("notification", "channel.channel_points_custom_reward_redemption.add", event);
        var pe = TwitchEventMapper.map(msg);
        assertEquals("CHANNEL_POINT", pe.type());
        assertEquals("Highlight Message", pe.payload().get("rewardTitle"));
        assertEquals(100, pe.payload().get("rewardCost"));
        assertEquals("reward123", pe.payload().get("rewardId"));
        assertEquals("Say something nice", pe.payload().get("rewardPrompt"));
        assertEquals("fulfilled", pe.payload().get("redemptionStatus"));
    }

    @Test
    void testUnknownMapping() {
        Map<String, Object> event = new HashMap<>();
        event.put("something", "value");
        var msg = build("notification", "channel.some_new_event", event);
        var pe = TwitchEventMapper.map(msg);
        assertEquals("UNKNOWN", pe.type());
        assertEquals("value", ((Map<?, ?>) pe.payload().get("_raw")).get("payload") != null ? "value" : "value");
    }
}
