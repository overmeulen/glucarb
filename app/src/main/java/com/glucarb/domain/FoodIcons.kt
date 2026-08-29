package com.glucarb.domain

/**
 * The built-in icon set offered as a lightweight alternative to a photo.
 *
 * Two deliberate constraints:
 *
 * 1. **Glyphs are declared as code points, never as literal characters.** The catalog is then
 *    pure ASCII, so it cannot be corrupted by an editor or a build step guessing the wrong
 *    source encoding.
 * 2. **Nothing newer than Emoji 5.0.** `minSdk` is 26 (Android 8.0), which shipped Unicode 10 /
 *    Emoji 5.0. A later glyph would render as an empty "tofu" box on the oldest supported
 *    devices, which is worse than showing no icon at all. Tempting but banned: bagel, cupcake,
 *    leafy green, mango, waffle, garlic, onion, olive, blueberries, flatbread, bubble tea.
 *
 * [keywords] must be lower-case and accent-free: the suggester normalises the user's input the
 * same way before comparing, and [FoodIconsTest] pins that invariant.
 * Multi-word keywords are matched against the whole name, single words against each token.
 */
enum class IconCategory(val label: String) {
    FRUIT("Fruit"),
    VEGETABLE("Vegetables"),
    GRAIN("Bread & grains"),
    DISH("Dishes"),
    PROTEIN("Protein & dairy"),
    SWEET("Sweets"),
    DRINK("Drinks"),
    OTHER("Other"),
}

data class FoodIcon(
    /** The rendered character, built once from [FoodIcons] code points. */
    val glyph: String,
    /** English display name, used for accessibility and for the browse sheet's search. */
    val label: String,
    val category: IconCategory,
    val keywords: List<String>,
)

object FoodIcons {

    private fun icon(
        vararg codePoints: Int,
        label: String,
        category: IconCategory,
        keywords: String,
    ): FoodIcon = FoodIcon(
        glyph = buildString { codePoints.forEach { appendCodePoint(it) } },
        label = label,
        category = category,
        keywords = keywords.split(' ').filter { it.isNotEmpty() },
    )

    // U+FE0F. Required after code points whose default presentation is text, not emoji.
    private const val VS16 = 0xFE0F

    val all: List<FoodIcon> = listOf(
        // ---- Fruit ----
        icon(0x1F34E, label = "Apple", category = IconCategory.FRUIT, keywords = "apple pomme"),
        icon(0x1F34F, label = "Green apple", category = IconCategory.FRUIT, keywords = "greenapple pommeverte granny"),
        icon(0x1F350, label = "Pear", category = IconCategory.FRUIT, keywords = "pear poire"),
        icon(0x1F34A, label = "Orange", category = IconCategory.FRUIT, keywords = "orange tangerine mandarin mandarine clementine satsuma"),
        icon(0x1F34B, label = "Lemon", category = IconCategory.FRUIT, keywords = "lemon citron lime"),
        icon(0x1F34C, label = "Banana", category = IconCategory.FRUIT, keywords = "banana banane plantain"),
        icon(0x1F349, label = "Watermelon", category = IconCategory.FRUIT, keywords = "watermelon pasteque"),
        icon(0x1F348, label = "Melon", category = IconCategory.FRUIT, keywords = "melon cantaloupe"),
        icon(0x1F347, label = "Grapes", category = IconCategory.FRUIT, keywords = "grape grapes raisin raisins"),
        icon(0x1F353, label = "Strawberry", category = IconCategory.FRUIT, keywords = "strawberry strawberries fraise fraises"),
        icon(0x1F352, label = "Cherries", category = IconCategory.FRUIT, keywords = "cherry cherries cerise cerises"),
        icon(0x1F351, label = "Peach", category = IconCategory.FRUIT, keywords = "peach peche nectarine apricot abricot"),
        icon(0x1F34D, label = "Pineapple", category = IconCategory.FRUIT, keywords = "pineapple ananas"),
        icon(0x1F95D, label = "Kiwi", category = IconCategory.FRUIT, keywords = "kiwi"),
        icon(0x1F965, label = "Coconut", category = IconCategory.FRUIT, keywords = "coconut coco noixdecoco"),
        icon(0x1F345, label = "Tomato", category = IconCategory.FRUIT, keywords = "tomato tomatoes tomate tomates"),
        icon(0x1F951, label = "Avocado", category = IconCategory.FRUIT, keywords = "avocado avocat guacamole"),

        // ---- Vegetables ----
        icon(0x1F955, label = "Carrot", category = IconCategory.VEGETABLE, keywords = "carrot carrots carotte carottes"),
        icon(0x1F33D, label = "Corn", category = IconCategory.VEGETABLE, keywords = "corn maize mais sweetcorn polenta"),
        icon(0x1F954, label = "Potato", category = IconCategory.VEGETABLE, keywords = "potato potatoes patate pommedeterre pommesdeterre mash puree gratin"),
        icon(0x1F360, label = "Sweet potato", category = IconCategory.VEGETABLE, keywords = "sweetpotato patatedouce yam"),
        icon(0x1F952, label = "Cucumber", category = IconCategory.VEGETABLE, keywords = "cucumber concombre pickle cornichon courgette zucchini"),
        icon(0x1F966, label = "Broccoli", category = IconCategory.VEGETABLE, keywords = "broccoli brocoli cabbage chou cauliflower choufleur"),
        icon(0x1F346, label = "Aubergine", category = IconCategory.VEGETABLE, keywords = "eggplant aubergine ratatouille"),
        icon(0x1F336, VS16, label = "Pepper", category = IconCategory.VEGETABLE, keywords = "pepper piment chili poivron paprika"),
        icon(0x1F344, label = "Mushroom", category = IconCategory.VEGETABLE, keywords = "mushroom mushrooms champignon champignons"),
        icon(0x1F957, label = "Salad", category = IconCategory.VEGETABLE, keywords = "salad salade lettuce laitue spinach epinards greens crudites"),
        icon(0x1F330, label = "Chestnut", category = IconCategory.VEGETABLE, keywords = "chestnut chataigne marron"),
        icon(0x1F95C, label = "Nuts", category = IconCategory.VEGETABLE, keywords = "peanut peanuts nuts noix cacahuete arachide amande almond noisette hazelnut pistache"),

        // ---- Bread & grains ----
        icon(0x1F35E, label = "Bread", category = IconCategory.GRAIN, keywords = "bread pain toast slice tranche loaf mie sandwichbread painmie"),
        icon(0x1F956, label = "Baguette", category = IconCategory.GRAIN, keywords = "baguette ficelle"),
        icon(0x1F950, label = "Croissant", category = IconCategory.GRAIN, keywords = "croissant viennoiserie painauchocolat chocolatine painauxraisins brioche"),
        icon(0x1F95E, label = "Pancakes", category = IconCategory.GRAIN, keywords = "pancake pancakes crepe crepes waffle gaufre blini"),
        icon(0x1F35A, label = "Rice", category = IconCategory.GRAIN, keywords = "rice riz risotto basmati"),
        icon(0x1F359, label = "Rice ball", category = IconCategory.GRAIN, keywords = "riceball onigiri"),
        icon(0x1F358, label = "Rice cracker", category = IconCategory.GRAIN, keywords = "ricecracker cracker senbei galette"),
        icon(0x1F35C, label = "Noodles", category = IconCategory.GRAIN, keywords = "noodle noodles ramen nouilles pho udon soba soup soupe"),
        icon(0x1F35D, label = "Pasta", category = IconCategory.GRAIN, keywords = "pasta pates spaghetti penne tagliatelle macaroni lasagne lasagna fusilli bolognaise carbonara"),
        icon(0x1F968, label = "Pretzel", category = IconCategory.GRAIN, keywords = "pretzel bretzel"),
        icon(0x1F963, label = "Cereal", category = IconCategory.GRAIN, keywords = "cereal cereals cereales muesli granola porridge oats avoine oatmeal bowl bol"),
        icon(0x1F33E, label = "Flour", category = IconCategory.GRAIN, keywords = "wheat flour farine ble grain semoule couscous quinoa boulgour"),

        // ---- Dishes ----
        icon(0x1F355, label = "Pizza", category = IconCategory.DISH, keywords = "pizza calzone"),
        icon(0x1F354, label = "Burger", category = IconCategory.DISH, keywords = "burger hamburger cheeseburger"),
        icon(0x1F35F, label = "Fries", category = IconCategory.DISH, keywords = "fries frites chips"),
        icon(0x1F32D, label = "Hot dog", category = IconCategory.DISH, keywords = "hotdog sausage saucisse merguez"),
        icon(0x1F32E, label = "Taco", category = IconCategory.DISH, keywords = "taco tacos tortilla nachos"),
        icon(0x1F32F, label = "Burrito", category = IconCategory.DISH, keywords = "burrito wrap fajita galettecomplete"),
        icon(0x1F96A, label = "Sandwich", category = IconCategory.DISH, keywords = "sandwich croquemonsieur panini club"),
        icon(0x1F959, label = "Kebab", category = IconCategory.DISH, keywords = "kebab pita gyros shawarma falafel"),
        icon(0x1F373, label = "Fried egg", category = IconCategory.DISH, keywords = "friedegg omelette omelet oeufplat brouilles scrambled"),
        icon(0x1F95A, label = "Egg", category = IconCategory.DISH, keywords = "egg eggs oeuf oeufs"),
        icon(0x1F372, label = "Stew", category = IconCategory.DISH, keywords = "stew soup soupe potage ragout casserole cassoulet potaufeu chili veloute"),
        icon(0x1F35B, label = "Curry", category = IconCategory.DISH, keywords = "curry tikka masala colombo"),
        icon(0x1F371, label = "Bento", category = IconCategory.DISH, keywords = "bento lunchbox plateaurepas"),
        icon(0x1F363, label = "Sushi", category = IconCategory.DISH, keywords = "sushi maki sashimi california"),
        icon(0x1F364, label = "Tempura", category = IconCategory.DISH, keywords = "tempura beignet fritter nem"),
        icon(0x1F95F, label = "Dumpling", category = IconCategory.DISH, keywords = "dumpling ravioli gyoza dimsum raviole"),
        icon(0x1F967, label = "Pie", category = IconCategory.DISH, keywords = "pie tart tarte quiche tourte pizzatarte"),
        icon(0x1F96B, label = "Canned food", category = IconCategory.DISH, keywords = "can canned conserve boite tin"),
        icon(0x1F362, label = "Skewer", category = IconCategory.DISH, keywords = "skewer brochette oden kebabskewer"),

        // ---- Protein & dairy ----
        icon(0x1F357, label = "Chicken", category = IconCategory.PROTEIN, keywords = "chicken poulet cuisse drumstick turkey dinde volaille"),
        icon(0x1F356, label = "Meat", category = IconCategory.PROTEIN, keywords = "meat viande ribs cote agneau lamb porc pork jambon ham"),
        icon(0x1F969, label = "Steak", category = IconCategory.PROTEIN, keywords = "steak beef boeuf entrecote filet"),
        icon(0x1F953, label = "Bacon", category = IconCategory.PROTEIN, keywords = "bacon lardon lardons lard pancetta"),
        icon(0x1F41F, label = "Fish", category = IconCategory.PROTEIN, keywords = "fish poisson salmon saumon tuna thon cabillaud cod"),
        icon(0x1F990, label = "Shrimp", category = IconCategory.PROTEIN, keywords = "shrimp crevette prawn crab crabe lobster homard seafood fruitsdemer"),
        icon(0x1F9C0, label = "Cheese", category = IconCategory.PROTEIN, keywords = "cheese fromage comte gruyere emmental cheddar parmesan mozzarella feta chevre"),
        icon(0x1F95B, label = "Milk", category = IconCategory.PROTEIN, keywords = "milk lait yaourt yogurt yoghurt fromageblanc skyr creme cream"),

        // ---- Sweets ----
        icon(0x1F36B, label = "Chocolate", category = IconCategory.SWEET, keywords = "chocolate chocolat cocoa cacao praline"),
        icon(0x1F36C, label = "Sweets", category = IconCategory.SWEET, keywords = "candy sweets bonbon bonbons gummy haribo"),
        icon(0x1F36D, label = "Lollipop", category = IconCategory.SWEET, keywords = "lollipop sucette"),
        icon(0x1F369, label = "Doughnut", category = IconCategory.SWEET, keywords = "donut doughnut beignet chouquette"),
        icon(0x1F36A, label = "Biscuit", category = IconCategory.SWEET, keywords = "cookie cookies biscuit biscuits sable speculoos petitbeurre"),
        icon(0x1F370, label = "Cake", category = IconCategory.SWEET, keywords = "cake gateau cheesecake fraisier partdegateau"),
        icon(0x1F382, label = "Birthday cake", category = IconCategory.SWEET, keywords = "birthdaycake gateaudanniversaire anniversaire"),
        icon(0x1F36E, label = "Custard", category = IconCategory.SWEET, keywords = "custard flan creme pudding cremebrulee panna tiramisu mousse"),
        icon(0x1F36F, label = "Honey", category = IconCategory.SWEET, keywords = "honey miel sirop syrup confiture jam sugar sucre"),
        icon(0x1F366, label = "Ice cream", category = IconCategory.SWEET, keywords = "icecream glace cone cornet softserve"),
        icon(0x1F368, label = "Ice cream tub", category = IconCategory.SWEET, keywords = "icecreamtub pot sorbet"),
        icon(0x1F367, label = "Shaved ice", category = IconCategory.SWEET, keywords = "shavedice granita"),
        icon(0x1F361, label = "Dango", category = IconCategory.SWEET, keywords = "dango mochi"),

        // ---- Drinks ----
        icon(0x2615, label = "Coffee", category = IconCategory.DRINK, keywords = "coffee cafe espresso latte cappuccino"),
        icon(0x1F375, label = "Tea", category = IconCategory.DRINK, keywords = "tea the infusion tisane matcha"),
        icon(0x1F964, label = "Soft drink", category = IconCategory.DRINK, keywords = "soda cola coca softdrink limonade milkshake smoothie"),
        icon(0x1F379, label = "Juice", category = IconCategory.DRINK, keywords = "juice jus jusdorange orangejuice cocktail punch"),
        icon(0x1F37A, label = "Beer", category = IconCategory.DRINK, keywords = "beer biere pint demi"),
        icon(0x1F377, label = "Wine", category = IconCategory.DRINK, keywords = "wine vin rouge blanc rose"),
        icon(0x1F942, label = "Champagne", category = IconCategory.DRINK, keywords = "champagne cremant prosecco toast"),
        icon(0x1F37E, label = "Bottle", category = IconCategory.DRINK, keywords = "bottle bouteille"),
        icon(0x1F943, label = "Spirits", category = IconCategory.DRINK, keywords = "whisky whiskey rhum rum vodka gin digestif"),
        icon(0x1F378, label = "Cocktail", category = IconCategory.DRINK, keywords = "martini aperitif apero"),
        icon(0x1F376, label = "Sake", category = IconCategory.DRINK, keywords = "sake"),
        icon(0x1F37C, label = "Baby bottle", category = IconCategory.DRINK, keywords = "babybottle biberon formula"),
        icon(0x1F4A7, label = "Water", category = IconCategory.DRINK, keywords = "water eau sparkling petillante"),

        // ---- Other ----
        icon(0x1F37D, VS16, label = "Meal", category = IconCategory.OTHER, keywords = "meal repas plat dish dinner diner lunch dejeuner breakfast petitdejeuner"),
        icon(0x1F374, label = "Restaurant", category = IconCategory.OTHER, keywords = "restaurant resto canteen cantine"),
        icon(0x1F944, label = "Spoon", category = IconCategory.OTHER, keywords = "spoon cuillere teaspoon tablespoon"),
        icon(0x1F961, label = "Takeaway", category = IconCategory.OTHER, keywords = "takeaway takeout emporter delivery livraison"),
        icon(0x1F960, label = "Fortune cookie", category = IconCategory.OTHER, keywords = "fortunecookie snack encas gouter"),
    )

    private val byGlyph: Map<String, FoodIcon> = all.associateBy { it.glyph }

    val byCategory: List<Pair<IconCategory, List<FoodIcon>>> =
        IconCategory.entries.map { category -> category to all.filter { it.category == category } }

    fun find(glyph: String?): FoodIcon? = glyph?.let { byGlyph[it] }

    /** Free-text search over labels and keywords, used by the browse-all sheet. */
    fun search(query: String): List<FoodIcon> {
        val q = EmojiSuggester.normalise(query)
        if (q.isBlank()) return all
        return all.filter { icon ->
            EmojiSuggester.normalise(icon.label).contains(q) || icon.keywords.any { it.contains(q) }
        }
    }
}
