package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** RER decodes the same written-book tag separately for each target chest. */
public final class RerStoryBookTags {
    // Only live RER entries keep a tag alive, including when a reload changes the book language or content.
    private static final Map<JsonPrimitive, WeakReference<JsonPrimitive>> TAGS = new WeakHashMap<>();

    private RerStoryBookTags() {}

    public static void shareIn(JsonObject table) {
        if (!(table.get("pools") instanceof JsonArray pools)) return;
        for (JsonElement pool : pools) {
            if (!(pool instanceof JsonObject object) || !(object.get("entries") instanceof JsonArray entries)) continue;
            for (JsonElement entry : entries) {
                if (entry instanceof JsonObject item && isVanillaId(item.get("name"), "written_book")
                        && item.get("functions") instanceof JsonArray functions) {
                    shareBookFunctions(functions);
                }
            }
        }
    }

    private static void shareBookFunctions(JsonArray functions) {
        for (JsonElement element : functions) {
            if (!(element instanceof JsonObject function) || !isVanillaId(function.get("function"), "set_nbt")
                    || !(function.get("tag") instanceof JsonPrimitive tag) || !tag.isString()) continue;
            String text = tag.getAsString();
            if (text.contains("ssc_book_id:") || text.contains("\"ssc_book_id\":")) {
                function.add("tag", sharedTag(tag));
            }
        }
    }

    private static synchronized JsonPrimitive sharedTag(JsonPrimitive tag) {
        WeakReference<JsonPrimitive> reference = TAGS.get(tag);
        JsonPrimitive previous = reference == null ? null : reference.get();
        if (previous != null) return previous;
        // Validate only the first occurrence of each distinct payload. A page can itself mention ssc_book_id.
        try {
            var book = StringNbtReader.parse(tag.getAsString());
            if (book.contains("ssc_book_id", NbtElement.STRING_TYPE) && book.contains("pages", NbtElement.LIST_TYPE)
                    && book.contains("author", NbtElement.STRING_TYPE) && book.contains("title", NbtElement.STRING_TYPE)
                    && book.contains("generation", NbtElement.NUMBER_TYPE)) {
                TAGS.put(tag, new WeakReference<>(tag));
            }
        } catch (CommandSyntaxException ignored) {
            // Leave other tags exactly as decoded by RER.
        }
        return tag;
    }

    private static boolean isVanillaId(JsonElement element, String path) {
        if (!(element instanceof JsonPrimitive value) || !value.isString()) return false;
        // RER strips the minecraft namespace when sending vanilla identifiers.
        String id = value.getAsString();
        return id.equals(path) || id.length() == 10 + path.length() && id.startsWith("minecraft:") && id.endsWith(path);
    }
}
