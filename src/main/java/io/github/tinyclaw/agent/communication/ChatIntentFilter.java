package io.github.tinyclaw.agent.communication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 聊天入口的确定性意图过滤器，避免普通群聊唤醒 Main Loop。
 */
public final class ChatIntentFilter {

    private static final ChatIntentFilter DISABLED = new ChatIntentFilter(false, List.of());

    private final boolean enabled;
    private final List<String> markers;

    private ChatIntentFilter(boolean enabled, List<String> markers) {
        this.enabled = enabled;
        this.markers = markers;
    }

    public static ChatIntentFilter of(boolean enabled, Collection<String> markers) {
        Objects.requireNonNull(markers, "markers");
        List<String> normalizedMarkers = new ArrayList<String>();
        for (String marker : markers) {
            if (marker != null && !marker.trim().isEmpty()) {
                normalizedMarkers.add(marker.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new ChatIntentFilter(enabled, List.copyOf(normalizedMarkers));
    }

    public static ChatIntentFilter disabled() {
        return DISABLED;
    }

    public boolean shouldStartAgent(String text) {
        if (!enabled) {
            return true;
        }
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String marker : markers) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
