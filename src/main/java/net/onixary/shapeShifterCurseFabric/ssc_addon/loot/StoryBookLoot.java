package net.onixary.shapeShifterCurseFabric.ssc_addon.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetNbtLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StoryBookLoot {

	private static final Logger log = LoggerFactory.getLogger(StoryBookLoot.class);

	private StoryBookLoot() {
        // This utility class should not be instantiated
    }

    private static float chance = 0.038f;
    private static List<BookData> loadedBooks = new ArrayList<>();
    private static SSCAddonConfig.BookLanguage loadedLanguage = null;
    
    public static class BookData {
        public String id;
        public String title;
        public String author;
        public String content;
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
	            log.error("Failed to load {}: file not found", fileName);
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
	    log.info("Loaded {} books from {}", loadedBooks.size(), fileName);
    }

    /**
     * 强制重新加载书籍列表（用于配置更新后刷新）
     */
    public static void reloadBooks() {
        loadedBooks.clear();
        loadedLanguage = null;
        loadConfig();
        loadBooks();
	    log.info("Books reloaded. Total: {}", loadedBooks.size());
    }

        /**
     * 获取所有书籍ID列表（用于命令自动补全）
     */
    public static List<String> getBookIds() {
        loadBooks();
        return loadedBooks.stream()
                .map(book -> book.id)
                .toList(); // 使用 Stream.toList() 替代 collect(Collectors.toList())
    }

    /**
     * 获取所有书籍ID列表（指定语言）
     */
    public static List<String> getBookIds(String language) {
        String fileName = (language != null && language.equalsIgnoreCase("en_us")) ? "books_en.json" : "books_cn.json";
        List<BookData> books = parseBookFile(fileName);
        return books.stream()
                .map(book -> book.id)
                .toList(); // 同样使用 Stream.toList()
    }


    /**
     * 获取书籍数量
     */
    public static int getBookCount() {
        loadBooks();
        return loadedBooks.size();
    }

    /**
     * 通过数字章节获取书籍（向后兼容）
     */
    public static net.minecraft.item.ItemStack getStoryBook(int chapter) {
        return getStoryBookById(String.valueOf(chapter), null);
    }
    
    /**
     * 获取所有书籍的ItemStack列表
     */
    public static List<net.minecraft.item.ItemStack> getAllStoryBooks() {
        loadBooks();
        List<net.minecraft.item.ItemStack> stacks = new ArrayList<>();
        for (BookData book : loadedBooks) {
            stacks.add(createBookStack(book.title, book.author, book.content));
        }
        return stacks;
    }

    /**
     * 通过数字章节和语言获取书籍（向后兼容）
     */
    public static net.minecraft.item.ItemStack getStoryBook(int chapter, String language) {
        return getStoryBookById(String.valueOf(chapter), language);
    }

    /**
     * 通过字符串ID获取书籍（主要方法）
     * @param bookId 书籍ID（JSON中定义的id字段）
     * @param language 语言（null使用配置，"en_us"或"zh_cn"指定）
     * @return 书籍ItemStack，如果未找到返回EMPTY
     */
    public static net.minecraft.item.ItemStack getStoryBookById(String bookId, String language) {
        List<BookData> sourceBooks;
        
        if (language != null && !language.isEmpty()) {
            String fileName = language.equalsIgnoreCase("en_us") ? "books_en.json" : "books_cn.json";
            sourceBooks = parseBookFile(fileName);
        } else {
            loadBooks();
            sourceBooks = loadedBooks;
        }

        for (BookData book : sourceBooks) {
            if (book.id.equals(bookId)) {
                return createBookStack(book.title, book.author, book.content);
            }
        }
        
        return net.minecraft.item.ItemStack.EMPTY;
    }

    /**
     * 通过ID获取书籍数据（不生成ItemStack）
     */
    public static BookData getBookDataById(String bookId, String language) {
        List<BookData> sourceBooks;
        
        if (language != null && !language.isEmpty()) {
            String fileName = language.equalsIgnoreCase("en_us") ? "books_en.json" : "books_cn.json";
            sourceBooks = parseBookFile(fileName);
        } else {
            loadBooks();
            sourceBooks = loadedBooks;
        }

        for (BookData book : sourceBooks) {
            if (book.id.equals(bookId)) {
                return book;
            }
        }
        
        return null;
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

    /**
     * 将长文本分割成适合 Minecraft 书籍的页面
     * <p>
     * 修复中文换页丢失文本的问题：
     * 1. 使用更保守的每页字符限制（中文字符显示宽度是英文的2倍）
     * 2. 逐字符处理，确保不丢失任何内容
     * 3. 优先在标点符号或空格处分页
     */
    private static List<String> splitIntoPages(String content) {
        List<String> pages = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return pages;
        }
        
        // Minecraft 书籍限制（保守值，确保安全）
        // 中文字符占用约2个显示单位，所以中文页面实际能容纳的字符数更少
        final int MAX_CHARS_PER_PAGE_LATIN = 256;  // 英文/数字
        final int MAX_CHARS_PER_PAGE_CJK = 140;    // 中文/日文/韩文（保守估计）
        final int MAX_LINES_PER_PAGE = 14;         // 最大行数
        final int CHARS_PER_LINE_CJK = 10;         // 中文每行约10-11个字符
        final int CHARS_PER_LINE_LATIN = 19;       // 英文每行约19-20个字符
        
        StringBuilder currentPage = new StringBuilder();
        int currentEstimatedLines = 0;
        
        // 将内容按行分割，保留空行
        String[] lines = content.split("\n", -1); // -1 保留末尾空字符串
        
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            
            // 计算这一行的显示行数
            int lineDisplayLines = calculateDisplayLines(line, CHARS_PER_LINE_CJK, CHARS_PER_LINE_LATIN);
            
            // 计算当前页面的有效字符数（考虑中文权重）
            int currentPageEffectiveChars = calculateEffectiveLength(currentPage.toString());
            int lineEffectiveChars = calculateEffectiveLength(line);
            
            // 检查是否需要换页
            boolean needNewPage = currentPageEffectiveChars + lineEffectiveChars > MAX_CHARS_PER_PAGE_CJK * 2;

            if (currentEstimatedLines + lineDisplayLines > MAX_LINES_PER_PAGE) {
                needNewPage = true;
            }
            
            if (needNewPage && !currentPage.isEmpty()) {
                // 保存当前页，开始新页
                pages.add(currentPage.toString());
                currentPage = new StringBuilder();
                currentEstimatedLines = 0;
            }
            
            // 检查单行是否超过页面容量，需要拆分
            if (lineEffectiveChars > MAX_CHARS_PER_PAGE_CJK * 2 || lineDisplayLines > MAX_LINES_PER_PAGE) {
                // 处理超长行：逐字符添加
                String remaining = line;
                while (!remaining.isEmpty()) {
                    int splitIndex = findSafeSplitPoint(remaining, MAX_CHARS_PER_PAGE_CJK, MAX_LINES_PER_PAGE, CHARS_PER_LINE_CJK);
                    
                    if (splitIndex <= 0) {
                        splitIndex = Math.min(remaining.length(), MAX_CHARS_PER_PAGE_CJK);
                    }
                    
                    String chunk = remaining.substring(0, splitIndex);
                    remaining = remaining.substring(splitIndex);
                    
                    // 如果当前页有内容且加上chunk会溢出，先保存当前页
                    if (!currentPage.isEmpty() &&
                        calculateEffectiveLength(currentPage.toString()) + calculateEffectiveLength(chunk) > MAX_CHARS_PER_PAGE_CJK * 2) {
                        pages.add(currentPage.toString());
                        currentPage = new StringBuilder();
                        currentEstimatedLines = 0;
                    }
                    
                    currentPage.append(chunk);
                    
                    // 如果还有剩余内容，保存当前页
                    if (!remaining.isEmpty()) {
                        pages.add(currentPage.toString());
                        currentPage = new StringBuilder();
                        currentEstimatedLines = 0;
                    }
                }
                // 添加换行符（如果不是最后一行）
                if (lineIndex < lines.length - 1) {
                    currentPage.append("\n");
                }
                currentEstimatedLines = calculateDisplayLines(currentPage.toString(), CHARS_PER_LINE_CJK, CHARS_PER_LINE_LATIN);
            } else {
                // 正常添加行
                currentPage.append(line);
                // 添加换行符（如果不是最后一行）
                if (lineIndex < lines.length - 1) {
                    currentPage.append("\n");
                }
                currentEstimatedLines += lineDisplayLines;
            }
        }
        
        // 保存最后一页
        if (!currentPage.isEmpty()) {
            pages.add(currentPage.toString());
        }
        
        // 调试输出
	    log.info("[StoryBookLoot] Split content into {} pages", pages.size());
        
        return pages;
    }
    
    /**
     * 计算文本的有效长度（中文算2，英文算1）
     */
    private static int calculateEffectiveLength(String text) {
        if (text == null) return 0;
        int length = 0;
        for (char c : text.toCharArray()) {
            if (isCJKCharacter(c)) {
                length += 2; // 中文字符算2个单位
            } else {
                length += 1; // 其他字符算1个单位
            }
        }
        return length;
    }
    
    /**
     * 计算文本需要的显示行数
     */
    private static int calculateDisplayLines(String text, int charsPerLineCJK, int charsPerLineLatin) {
        if (text == null || text.isEmpty()) return 1;
        
        int lines = 0;
        int currentLineWidth = 0;
        // 中文行宽约10-11字符，对应显示宽度约20-22单位
        int maxLineWidth = charsPerLineCJK * 2;
        
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                lines++;
                currentLineWidth = 0;
                continue;
            }
            
            int charWidth = isCJKCharacter(c) ? 2 : 1;
            currentLineWidth += charWidth;
            
            if (currentLineWidth >= maxLineWidth) {
                lines++;
                currentLineWidth = 0;
            }
        }
        
        // 如果最后一行有内容，计入
        if (currentLineWidth > 0) {
            lines++;
        }
        
        return Math.max(1, lines);
    }
    
    /**
     * 找到安全的分割点（优先在标点符号或空格处分割）
     */
    private static int findSafeSplitPoint(String text, int maxChars, int maxLines, int charsPerLineCJK) {
        if (text.length() <= maxChars) {
            return text.length();
        }
        
        // 计算不超过限制的最大索引
        int effectiveLength = 0;
        int safeIndex = 0;
        int lastPunctuationIndex = -1;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charWidth = isCJKCharacter(c) ? 2 : 1;
            
            if (effectiveLength + charWidth > maxChars * 2) {
                break;
            }
            
            effectiveLength += charWidth;
            safeIndex = i + 1;
            
            // 记录最后一个标点符号的位置
            if (isPunctuation(c)) {
                lastPunctuationIndex = i + 1;
            }
        }
        
        // 优先在标点符号处分割
        if (lastPunctuationIndex > safeIndex / 2) {
            return lastPunctuationIndex;
        }
        
        return safeIndex;
    }
    
    /**
     * 判断是否是CJK（中日韩）字符
     */
    private static boolean isCJKCharacter(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
               block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
               block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
               block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
               block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
               block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
               block == Character.UnicodeBlock.HIRAGANA ||
               block == Character.UnicodeBlock.KATAKANA ||
               block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
    
    /**
     * 判断是否是标点符号（适合作为分割点）
     */
    private static boolean isPunctuation(char c) {
        // 英文标点
        if (c == ' ' || c == ',' || c == '.' || c == '!' || c == '?') {
            return true;
        }
        /* ---中文标点（使用Unicode码点避免字符常量问题）---
        转义序列不再建议使用，在开发中所有项目理应使用UTF-8编码，直接使用字符本身即可，提升可读性
           ， = \uFF0C, 。 = \u3002, ！ = \uFF01, ？ = \uFF1F, ； = \uFF1B
           ： = \uFF1A, " = \u201C, " = \u201D, ' = \u2018, ' = \u2019
           、 = \u3001, ） = \uFF09, 】 = \u3011, 」 = \u300D, 》 = \u300B
        */
        if (c == '，' || c == '。' || c == '！' || c == '？' || c == '；') {
            return true;
        }
        if (c == '：' || c == '“' || c == '”' || c == '‘' || c == '’') {
            return true;
        }
	    return c == '、' || c == '）' || c == '】' || c == '」' || c == '》';
    }

}
