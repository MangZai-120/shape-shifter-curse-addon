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

    private static List<BookData> parseBookFile(String fileName) {
        List<BookData> books = new ArrayList<>();
        try (InputStream is = StoryBookLoot.class.getResourceAsStream("/data/ssc_addon/story_books/" + fileName)) {
            if (is == null) {
                System.err.println("Failed to load " + fileName + ": file not found");
                return books;
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
                
                books.add(bookData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return books;
    }

    private static void loadBooks() {
        SSCAddonConfig config = AutoConfig.getConfigHolder(SSCAddonConfig.class).getConfig();
        // Reload if language changed or not loaded
        if (!loadedBooks.isEmpty() && loadedLanguage == config.bookLanguage) return;
        
        loadedBooks.clear();
        loadedLanguage = config.bookLanguage;
        
        String fileName = (config.bookLanguage == SSCAddonConfig.BookLanguage.ENGLISH) ? "books_en.json" : "books_cn.json";
        
        loadedBooks = parseBookFile(fileName);
        System.out.println("Loaded " + loadedBooks.size() + " books from " + fileName);
    }

    public static net.minecraft.item.ItemStack getStoryBook(int chapter) {
        return getStoryBook(chapter, null);
    }

    public static net.minecraft.item.ItemStack getStoryBook(int chapter, String language) {
        List<BookData> sourceBooks;
        
        if (language != null && !language.isEmpty()) {
            String fileName = language.equalsIgnoreCase("en_us") ? "books_en.json" : "books_cn.json";
            sourceBooks = parseBookFile(fileName);
        } else {
            loadBooks();
            sourceBooks = loadedBooks;
        }

        String targetId = String.valueOf(chapter);
        for (BookData book : sourceBooks) {
            if (book.id.equals(targetId)) {
                return createBookStack(book.title, book.author, book.content);
            }
        }
        
        return net.minecraft.item.ItemStack.EMPTY;
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
        // Split by newlines. No regex special handling needed for standard \n
        String[] paragraphs = content.split("\n");
        StringBuilder currentPage = new StringBuilder();
        
        int PAGE_MAX_CHARS = 200; 
        int MAX_LINES = 13; 
        int currentLines = 0;

        for (String para : paragraphs) {
            // Note: intentionally NOT skipping empty lines to preserve poetry stanzas.
            
            int paraLen = para.length();
            // Heuristic: 1 line for every ~16 characters (conservative for CJK)
            // Empty line = 1 line height.
            int estimatedLines = (paraLen < 1) ? 1 : (int)Math.ceil((double)paraLen / 16.0);

            // 1. Check if we need a new page before adding this paragraph
            boolean overflowChars = (currentPage.length() + paraLen > PAGE_MAX_CHARS);
            boolean overflowLines = (currentLines + estimatedLines > MAX_LINES);

            if (overflowChars || overflowLines) {
                 if (currentPage.length() > 0) {
                     pages.add(currentPage.toString());
                     currentPage = new StringBuilder();
                     currentLines = 0;
                 }
            }

            // 2. Add paragraph (handling potentially huge paragraphs)
            // If the paragraph itself is larger than a page, we split it into chunks
            String remaining = para;
            
            // While the remaining text is too big for a single fresh page...
            while (remaining.length() > PAGE_MAX_CHARS) {
                 // Take a chunk
                 int splitIndex = PAGE_MAX_CHARS;
                 int lastSpace = remaining.substring(0, PAGE_MAX_CHARS).lastIndexOf(' ');
                 if (lastSpace > PAGE_MAX_CHARS / 2) {
                     splitIndex = lastSpace + 1;
                 }
                 
                 String chunk = remaining.substring(0, splitIndex);
                 pages.add(chunk); // Add as its own page
                 remaining = remaining.substring(splitIndex).trim(); // Prepare next chunk
            }
            
            // Append remaining (or original small para) to current buffer
            currentPage.append(remaining).append("\n");
            
            // Update estimates based on what we actually added to the specific buffer
            // (Re-calculate lines for the added part)
            int addedLines = (remaining.length() < 1) ? 1 : (int)Math.ceil((double)remaining.length() / 16.0);
            currentLines += addedLines;
        }
        
        if (currentPage.length() > 0) {
            pages.add(currentPage.toString());
        }
        return pages;
    }

}
