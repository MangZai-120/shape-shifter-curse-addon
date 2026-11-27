package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public final class RerStoryBookTagsTest {
    public static int run() {
        String chinese = "{author:'A',generation:0,pages:['甲'],ssc_book_id:'1',title:'书'}";
        JsonPrimitive first = decode("written_book", "set_nbt", chinese);
        require(first == decode("minecraft:written_book", "minecraft:set_nbt", chinese), "Equal books share a tag");
        String english = chinese.replace("甲", "A").replace("书", "Book");
        JsonPrimitive translated = decode("written_book", "set_nbt", english);
        require(first != translated && translated.getAsString().equals(english), "Translations keep their own exact text");
        require(first != decode("written_book", "set_nbt", chinese.replace("甲", "乙")), "Edited text at the same ID survives reload");
        require(first != decode("mod:written_book", "set_nbt", chinese), "Other items retain their own tag");
        require(first != decode("written_book", "mod:set_nbt", chinese), "Other functions retain their own tag");
        String normalBook = "{author:'A',generation:0,pages:['ssc_book_id:1'],title:'Other'}";
        require(decode("written_book", "set_nbt", normalBook) != decode("written_book", "set_nbt", normalBook),
                "A marker inside page text does not opt in ordinary books");
        String malformed = "{ssc_book_id:";
        require(decode("written_book", "set_nbt", malformed) != decode("written_book", "set_nbt", malformed),
                "Unrecognized tag text passes through unchanged");
        return 7;
    }

    private static JsonPrimitive decode(String item, String functionId, String text) {
        JsonObject table = JsonParser.parseString("{\"pools\":[{\"entries\":[{\"functions\":[{}]}]}]}").getAsJsonObject();
        JsonObject entry = table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        JsonObject function = entry.getAsJsonArray("functions").get(0).getAsJsonObject();
        entry.addProperty("name", item);
        function.addProperty("function", functionId);
        function.addProperty("tag", text);
        String before = table.toString();
        RerStoryBookTags.shareIn(table);
        require(before.equals(table.toString()), "Every serialized field and its order must survive sharing");
        return function.getAsJsonPrimitive("tag");
    }

    private static void require(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
}
