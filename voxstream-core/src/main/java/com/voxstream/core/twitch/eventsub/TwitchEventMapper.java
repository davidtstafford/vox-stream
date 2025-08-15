package com.voxstream.core.twitch.eventsub;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.voxstream.core.event.EventType;

/**
 * Maps raw EventSubMessage objects to generic TwitchEventPlatformEvent platform
 * events whose type aligns with the internal EventType enum (string form).
 * Extracts a standardized subset of fields for known types while preserving
 * full raw root map under key "_raw".
 */
public final class TwitchEventMapper {

    private TwitchEventMapper() {
    }

    public static TwitchEventPlatformEvent map(EventSubMessage msg) {
        if (msg == null)
            return null;
        String internalType = mapType(msg.subscriptionType(), msg.metadataType());
        Map<String, Object> standardized = buildStandardizedPayload(internalType, msg);
        return new TwitchEventPlatformEvent(Instant.now(), "twitch", internalType, standardized);
    }

    private static String mapType(String subscriptionType, String metadataType) {
        if (subscriptionType == null) {
            return "SYSTEM"; // welcome / keepalive
        }
        String t = subscriptionType.toLowerCase(Locale.ROOT);
        switch (t) {
            case "channel.subscribe":
            case "channel.subscription.end":
            case "channel.subscription.gift":
            case "channel.subscription.message":
                return EventType.SUBSCRIPTION.name();
            case "channel.cheer":
                return EventType.BITS.name();
            case "channel.follow":
                return EventType.FOLLOW.name();
            case "channel.raid":
                return EventType.RAID.name();
            case "channel.channel_points_custom_reward_redemption.add":
                return EventType.CHANNEL_POINT.name();
            case "channel.host": // deprecated but placeholder
                return EventType.HOST.name();
            default:
                return EventType.UNKNOWN.name();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildStandardizedPayload(String internalType, EventSubMessage msg) {
        Map<String, Object> root = msg.payload();
        Map<String, Object> out = new HashMap<>();
        out.put("eventType", internalType);
        out.put("_raw", root); // retain entire raw structure
        Object payloadObj = root.get("payload");
        if (!(payloadObj instanceof Map))
            return out;
        Map<String, Object> payload = (Map<String, Object>) payloadObj;
        Object eventObj = payload.get("event");
        if (!(eventObj instanceof Map))
            return out;
        Map<String, Object> event = (Map<String, Object>) eventObj;
        switch (internalType) {
            case "FOLLOW":
                copy(event, out, "user_id", "user_login", "broadcaster_user_id", "broadcaster_user_login");
                rename(out, "user_id", "userId");
                rename(out, "user_login", "userLogin");
                rename(out, "broadcaster_user_id", "broadcasterId");
                rename(out, "broadcaster_user_login", "broadcasterLogin");
                break;
            case "SUBSCRIPTION":
                copy(event, out, "user_id", "user_login", "broadcaster_user_id", "broadcaster_user_login",
                        "is_gift", "tier", "cumulative_months", "streak_months", "duration_months",
                        "message", "gifter_user_id", "gifter_user_login");
                rename(out, "user_id", "userId");
                rename(out, "user_login", "userLogin");
                rename(out, "broadcaster_user_id", "broadcasterId");
                rename(out, "broadcaster_user_login", "broadcasterLogin");
                // message may be nested { text: "..." }
                Object messageObj = out.get("message");
                if (messageObj instanceof Map) {
                    Object text = ((Map<?, ?>) messageObj).get("text");
                    if (text instanceof String) {
                        out.put("messageText", text);
                    }
                }
                rename(out, "gifter_user_id", "gifterUserId");
                rename(out, "gifter_user_login", "gifterUserLogin");
                // camelCase normalization
                rename(out, "is_gift", "isGift");
                rename(out, "cumulative_months", "cumulativeMonths");
                rename(out, "streak_months", "streakMonths");
                rename(out, "duration_months", "durationMonths");
                break;
            case "BITS":
                copy(event, out, "user_id", "user_login", "broadcaster_user_id", "broadcaster_user_login", "bits",
                        "is_anonymous");
                rename(out, "user_id", "userId");
                rename(out, "user_login", "userLogin");
                rename(out, "broadcaster_user_id", "broadcasterId");
                rename(out, "broadcaster_user_login", "broadcasterLogin");
                rename(out, "is_anonymous", "isAnonymous");
                break;
            case "RAID":
                copy(event, out, "from_broadcaster_user_id", "from_broadcaster_user_login",
                        "to_broadcaster_user_id", "to_broadcaster_user_login", "viewers");
                rename(out, "from_broadcaster_user_id", "fromBroadcasterId");
                rename(out, "from_broadcaster_user_login", "fromBroadcasterLogin");
                rename(out, "to_broadcaster_user_id", "toBroadcasterId");
                rename(out, "to_broadcaster_user_login", "toBroadcasterLogin");
                break;
            case "CHANNEL_POINT":
                copy(event, out, "user_id", "user_login", "broadcaster_user_id", "broadcaster_user_login",
                        "user_input", "status");
                rename(out, "user_id", "userId");
                rename(out, "user_login", "userLogin");
                rename(out, "broadcaster_user_id", "broadcasterId");
                rename(out, "broadcaster_user_login", "broadcasterLogin");
                Object rewardObj = event.get("reward");
                if (rewardObj instanceof Map) {
                    Object title = ((Map<?, ?>) rewardObj).get("title");
                    Object cost = ((Map<?, ?>) rewardObj).get("cost");
                    Object id = ((Map<?, ?>) rewardObj).get("id");
                    Object prompt = ((Map<?, ?>) rewardObj).get("prompt");
                    if (title != null)
                        out.put("rewardTitle", title);
                    if (cost != null)
                        out.put("rewardCost", cost);
                    if (id != null)
                        out.put("rewardId", id);
                    if (prompt != null)
                        out.put("rewardPrompt", prompt);
                }
                rename(out, "user_input", "userInput");
                rename(out, "status", "redemptionStatus");
                break;
            case "HOST":
                // Placeholder; Twitch deprecated hosts. Keep raw.
                break;
            default:
                // Unknown type - only raw preserved
                break;
        }
        return out;
    }

    private static void copy(Map<String, Object> src, Map<String, Object> dest, String... keys) {
        for (String k : keys) {
            Object v = src.get(k);
            if (v != null)
                dest.put(k, v);
        }
    }

    private static void rename(Map<String, Object> map, String oldKey, String newKey) {
        if (map.containsKey(oldKey)) {
            map.put(newKey, map.remove(oldKey));
        }
    }
}
