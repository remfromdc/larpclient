package dev.larp.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class CapeSetting extends Setting<String> {
    public CapeSetting() {
        super("cape", "larpclient");
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) set(json.getAsString());
    }
}
