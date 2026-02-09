package net.onixary.shapeShifterCurseFabric.ssc_addon.loot;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.LootTables;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.minecraft.loot.function.SetNbtLootFunction;
import net.minecraft.text.Text;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.autoconfig.AutoConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;

public class StoryBookLoot {

    private static float chance = 0.038f;
    private static List<BookData> loadedBooks = new ArrayList<>();
    private static SSCAddonConfig.BookLanguage loadedLanguage = null;
    
    private static class BookData {
        String id;
        String title;
        String author;
        String content;
    }

    private static void loadConfig() {
        try (InputStream is = StoryBookLoot.class.getResourceAsStream("/data/ssc_addon/story_books/config.json")) {
            if (is != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("chance")) {
                    chance = root.get("chance").getAsFloat();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadBooks() {
        SSCAddonConfig config = AutoConfig.getConfigHolder(SSCAddonConfig.class).getConfig();
        // Reload if language changed or not loaded
        if (!loadedBooks.isEmpty() && loadedLanguage == config.bookLanguage) return;
        
        loadedBooks.clear();
        loadedLanguage = config.bookLanguage;
        
        String fileName = (config.bookLanguage == SSCAddonConfig.BookLanguage.ENGLISH) ? "books_en.json" : "books_cn.json";
        
        try (InputStream is = StoryBookLoot.class.getResourceAsStream("/data/ssc_addon/story_books/" + fileName)) {
            if (is == null) {
                System.err.println("Failed to load " + fileName + ": file not found");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray booksArray = root.getAsJsonArray("books");
            
            for (JsonElement element : booksArray) {
                JsonObject bookObj = element.getAsJsonObject();
                BookData bookData = new BookData();
                bookData.id = bookObj.get("id").getAsString();
                
                bookData.title = bookObj.get("title").getAsString();
                bookData.author = bookObj.get("author").getAsString();
                bookData.content = bookObj.get("content").getAsString();
                
                loadedBooks.add(bookData);
            }
            System.out.println("Loaded " + loadedBooks.size() + " books from " + fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static net.minecraft.item.ItemStack getStoryBook(int chapter) {
        return getStoryBook(chapter, null);
    }

    public static net.minecraft.item.ItemStack getStoryBook(int chapter, String language) {
        loadBooks();
        if (chapter < 1 || chapter > loadedBooks.size()) {
            return net.minecraft.item.ItemStack.EMPTY;
        }

        BookData book = loadedBooks.get(chapter - 1);
        return createBookStack(book.title, book.author, book.content);
    }

    private static net.minecraft.item.ItemStack createBookStack(String title, String author, String content) {
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(Items.WRITTEN_BOOK);
        stack.setNbt(createBookNbt(title, author, content));
        return stack;
    }

    private static NbtCompound createBookNbt(String title, String author, String content) {
        NbtCompound nbt = new NbtCompound();
        // Truncate title to 32 chars max (Vanilla limit)
        String safeTitle = title;
        if (safeTitle.length() > 32) {
            safeTitle = safeTitle.substring(0, 32);
        }
        nbt.putString("title", safeTitle);
        nbt.putString("author", author);
        nbt.putInt("generation", 0);

        NbtList pages = new NbtList();
        // Split content into pages
        List<String> contentList = splitIntoPages(content);
        for (String pageText : contentList) {
            // Use Gson to create a safe JSON object string for the page
            JsonObject pageJson = new JsonObject();
            pageJson.addProperty("text", pageText);
            pages.add(NbtString.of(pageJson.toString()));
        }
        nbt.put("pages", pages);
        return nbt;
    }

    public static void init() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (isTargetChest(id)) {
                loadConfig();
                loadBooks();
                LootPool.Builder poolBuilder = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1.0f))
                        .conditionally(net.minecraft.loot.condition.RandomChanceLootCondition.builder(chance).build());

                for (BookData book : loadedBooks) {
                    addBookToPool(poolBuilder, book.title, book.author, book.content);
                }
                
                tableBuilder.pool(poolBuilder);
            }
        });
    }

    private static boolean isTargetChest(Identifier id) {
        String path = id.getPath();
        return path.startsWith("chests/village") ||
               path.equals("chests/abandoned_mineshaft") ||
               path.equals("chests/igloo_chest") ||
               path.equals("chests/simple_dungeon") ||
               path.equals("chests/woodland_mansion") ||
               path.startsWith("chests/underwater_ruin") ||
               path.startsWith("chests/shipwreck") ||
               path.equals("chests/buried_treasure") ||
               path.equals("chests/ruined_portal");
    }

    private static void addBookToPool(LootPool.Builder pool, String title, String author, String content) {
        pool.with(ItemEntry.builder(Items.WRITTEN_BOOK)
                .apply(SetNbtLootFunction.builder(createBookNbt(title, author, content)))
                .weight(1)); // Equal weight for each book
    }

    private static List<String> splitIntoPages(String content) {
        List<String> pages = new ArrayList<>();
        // Split by newlines first to preserve paragraphs
        String[] paragraphs = content.split("\n");
        StringBuilder currentPage = new StringBuilder();
        int PAGE_MAX = 200; // Safe limit

        for (String para : paragraphs) {
            if (para.trim().isEmpty()) continue;
            
            // If adding this paragraph exceeds limit...
            if (currentPage.length() + para.length() > PAGE_MAX) {
                 // If current page has content, save it
                 if (currentPage.length() > 0) {
                     pages.add(currentPage.toString());
                     currentPage = new StringBuilder();
                 }
                 
                 // Handle long paragraph
                 String remaining = para;
                 while (remaining.length() > PAGE_MAX) {
                     // Try to find a space near the limit to break cleanly
                     int splitIndex = PAGE_MAX;
                     // Look back for space
                     int lastSpace = remaining.substring(0, PAGE_MAX).lastIndexOf(' ');
                     if (lastSpace > PAGE_MAX / 2) { // Only if space is reasonably far
                         splitIndex = lastSpace + 1; // split at space.
                     }
                     
                     pages.add(remaining.substring(0, splitIndex));
                     remaining = remaining.substring(splitIndex).trim(); // trim leading space
                 }
                 currentPage.append(remaining).append("\n");
            } else {
                currentPage.append(para).append("\n");
            }
        }
        if (currentPage.length() > 0) {
            pages.add(currentPage.toString());
        }
        return pages;
    }

}
