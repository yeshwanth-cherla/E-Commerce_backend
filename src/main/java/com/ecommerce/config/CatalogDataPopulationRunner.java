package com.ecommerce.config;

import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Ensures a real, database-backed catalog of exactly 6 main categories
 * and 10 realistic Indian e-commerce products per category (60 products total).
 *
 * Idempotent: safe to run on application startup without duplicating data.
 * Safe: preserves historical order item references by soft-disabling legacy records.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogDataPopulationRunner implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public static final List<String> MAIN_CATEGORIES = List.of(
            "Grocery",
            "Electronics",
            "Fashion",
            "Home & Kitchen",
            "Beauty & Personal Care",
            "Books & Stationery"
    );

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting catalog verification and population check...");

        // 1. Ensure exactly the six main categories exist with no parent
        Map<String, Category> categoryMap = new HashMap<>();
        for (String catName : MAIN_CATEGORIES) {
            Category category = categoryRepository.findByName(catName)
                    .orElseGet(() -> {
                        Category c = new Category();
                        c.setName(catName);
                        c.setDescription("Quality " + catName + " products");
                        c.setParent(null);
                        return categoryRepository.save(c);
                    });

            if (category.getParent() != null) {
                category.setParent(null);
                category = categoryRepository.save(category);
            }
            categoryMap.put(catName, category);
        }

        Category electronicsCat = categoryMap.get("Electronics");

        // 2. Prepare 60 target product definitions
        List<ProductSeed> targetProducts = getTargetProductSeeds();
        Set<String> targetNames = new HashSet<>();
        for (ProductSeed seed : targetProducts) {
            targetNames.add(seed.name);
        }

        // 3. Handle existing database products:
        // Any legacy/demo products not in our 60 target products are re-parented to Electronics and soft-disabled
        // so that historical orders, order items, and audit records remain 100% intact without foreign key errors.
        List<Product> allDbProducts = productRepository.findAll();
        for (Product p : allDbProducts) {
            if (!targetNames.contains(p.getName())) {
                boolean modified = false;
                if (p.getCategory() == null || !categoryMap.containsValue(p.getCategory())) {
                    p.setCategory(electronicsCat);
                    modified = true;
                }
                if (p.isEnabled()) {
                    p.setEnabled(false);
                    modified = true;
                }
                if (modified) {
                    productRepository.save(p);
                    log.info("Soft-disabled legacy test product and linked to Electronics: ID={}, Name='{}'",
                            p.getId(), p.getName());
                }
            }
        }

        // 4. Safe removal of all subcategories and legacy test categories (strictly 6 main categories)
        // First, safely re-parent any products referencing subcategories or non-main categories
        for (Category c : categoryRepository.findAll()) {
            if (c.getParent() != null || !categoryMap.containsKey(c.getName())) {
                for (Product p : productRepository.findAll()) {
                    if (p.getCategory() != null && p.getCategory().getId().equals(c.getId())) {
                        Category targetCat = (c.getParent() != null && categoryMap.containsValue(c.getParent()))
                                ? c.getParent()
                                : electronicsCat;
                        p.setCategory(targetCat);
                        productRepository.save(p);
                        log.info("Re-assigned product ID={} from category '{}' to '{}'",
                                p.getId(), c.getName(), targetCat.getName());
                    }
                }
            }
        }

        // Delete any subcategories
        for (Category c : categoryRepository.findAll()) {
            if (c.getParent() != null) {
                categoryRepository.delete(c);
                log.info("Removed subcategory: ID={}, Name='{}'", c.getId(), c.getName());
            }
        }

        // Delete any non-main categories
        for (Category c : categoryRepository.findAll()) {
            if (!categoryMap.containsKey(c.getName())) {
                categoryRepository.delete(c);
                log.info("Removed legacy category: ID={}, Name='{}'", c.getId(), c.getName());
            }
        }

        // 5. Populate / Upsert the 60 target products
        for (ProductSeed seed : targetProducts) {
            Category cat = categoryMap.get(seed.categoryName);
            if (cat == null) continue;

            Optional<Product> existingOpt = productRepository.findAll().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(seed.name))
                    .findFirst();

            if (existingOpt.isPresent()) {
                Product p = existingOpt.get();
                p.setDescription(seed.description);
                p.setPrice(seed.price);
                p.setStockQuantity(seed.stockQuantity);
                p.setImageUrl(seed.imageUrl);
                p.setCategory(cat);
                p.setEnabled(true);
                productRepository.save(p);
            } else {
                Product p = new Product();
                p.setName(seed.name);
                p.setDescription(seed.description);
                p.setPrice(seed.price);
                p.setStockQuantity(seed.stockQuantity);
                p.setImageUrl(seed.imageUrl);
                p.setCategory(cat);
                p.setEnabled(true);
                productRepository.save(p);
                log.info("Created catalog product: [{}] '{}' - INR {}", seed.categoryName, seed.name, seed.price);
            }
        }

        // 6. Log final catalog summary
        log.info("=== CATALOG INITIALIZATION COMPLETE ===");
        for (String catName : MAIN_CATEGORIES) {
            Category c = categoryMap.get(catName);
            long count = productRepository.findAll().stream()
                    .filter(p -> p.isEnabled() && p.getCategory() != null && p.getCategory().getId().equals(c.getId()))
                    .count();
            log.info("Category: '{}' (ID {}) -> {} active products", catName, c.getId(), count);
        }
    }

    private static class ProductSeed {
        String categoryName;
        String name;
        String description;
        BigDecimal price;
        int stockQuantity;
        String imageUrl;

        ProductSeed(String categoryName, String name, String description, double price, int stockQuantity, String imageUrl) {
            this.categoryName = categoryName;
            this.name = name;
            this.description = description;
            this.price = BigDecimal.valueOf(price);
            this.stockQuantity = stockQuantity;
            this.imageUrl = imageUrl;
        }
    }

    private List<ProductSeed> getTargetProductSeeds() {
        List<ProductSeed> list = new ArrayList<>();

        // ──────────────── Grocery (10 products) ────────────────
        list.add(new ProductSeed("Grocery", "Tata Sampann Unpolished Toor Dal (1kg)",
                "Rich in protein and dietary fiber, natural unpolished toor dal for everyday nutritious meals.",
                165.00, 80, "https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "Fortune Sunlite Refined Sunflower Oil (1L Pouch)",
                "Enriched with Vitamins A and D, lightweight and healthy cooking oil suitable for all frying and curry preparations.",
                145.00, 100, "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "India Gate Classic Basmati Rice (5kg)",
                "Aged long grain aromatic basmati rice with slender pearls, ideal for biryanis and special occasions.",
                799.00, 50, "https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "Aashirvaad Superior MP Sharbati Whole Wheat Atta (5kg)",
                "100% pure whole wheat flour processed with traditional chakki grinding for extra soft rotis.",
                285.00, 75, "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "Taj Mahal Premium Tea (500g)",
                "Finest blend of tea leaves giving rich aroma, golden color, and strong invigorating flavour in every cup.",
                340.00, 60, "https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "Nescafe Classic Instant Coffee Jar (200g)",
                "Rich and aromatic 100% pure instant coffee crafted from select roasted Robusta beans.",
                575.00, 45, "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "Kellogg's Real Almond & Honey Corn Flakes (1kg)",
                "Crispy golden toasted corn flakes packed with crunchy real California almonds and natural honey.",
                420.00, 55, "https://images.unsplash.com/photo-1525351484163-7529414344d8?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "Nutraj California Raw Almonds / Badam (500g)",
                "Crunchy premium quality whole California almonds packed with Vitamin E, protein, and heart-healthy fats.",
                499.00, 65, "https://images.unsplash.com/photo-1508061253366-f7da158b6d46?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "Saffola Masala Oats Classic Masala (1kg)",
                "Delicious whole grain rolled oats blended with authentic Indian aromatic spices and real dried veggies.",
                210.00, 90, "https://images.unsplash.com/photo-1614961908595-81206f376cf2?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Grocery", "Dabur 100% Pure Squeezy Honey (500g)",
                "Pure naturally sourced honey with no added sugar or artificial preservatives for daily immunity.",
                230.00, 85, "https://images.unsplash.com/photo-1587049352846-4a222e784d38?auto=format&fit=crop&w=600&q=80"));

        // ──────────────── Electronics (10 products) ────────────────
        list.add(new ProductSeed("Electronics", "Sony WH-1000XM5 Wireless Noise Cancelling Headphones",
                "Industry-leading active noise cancellation with 30-hour battery life, 8 microphones, and LDAC audio.",
                29990.00, 25, "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "Apple iPhone 15 (128GB, Black)",
                "Dynamic Island, 48MP main camera, USB-C connector, and durable color-infused glass aluminum design.",
                69999.00, 18, "https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "Dell Inspiron 15 Core i5 12th Gen Laptop (16GB/512GB SSD)",
                "15.6-inch FHD anti-glare display, Intel Core i5 processor, lightweight chassis, and Windows 11 Home.",
                52990.00, 20, "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "Samsung Galaxy Tab S9 FE (10.9 inch, Wi-Fi, 128GB)",
                "Vibrant high-resolution screen with in-box S Pen, IP68 water resistance, and long-lasting 8000mAh battery.",
                34999.00, 22, "https://images.unsplash.com/photo-1561154464-82e9adf32764?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "Boat Airdopes 141 Bluetooth True Wireless Earbuds",
                "Up to 42 hours total playtime, ENx environmental noise cancellation, Beast mode low latency gaming.",
                1299.00, 85, "https://images.unsplash.com/photo-1590658268037-6bf12165a8df?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "Logitech MX Master 3S Wireless Ergonomic Mouse",
                "Quiet click switches, 8K DPI any-surface laser tracking, and ultra-fast MagSpeed electromagnetic scroll.",
                8995.00, 35, "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "Apple Watch SE (2nd Gen, GPS 40mm)",
                "Fitness and sleep tracking, crash detection, continuous optical heart rate sensor, and Retina OLED display.",
                24900.00, 30, "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "SanDisk Extreme 1TB Portable External NVMe SSD",
                "Fast NVMe solid state performance with up to 1050MB/s read speeds, USB-C 3.2, and IP55 water resistance.",
                8499.00, 40, "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "Anker 65W GaN Fast Wall Charger (3-Port USB-C)",
                "High-speed charging for laptops, tablets, and phones simultaneously with PowerIQ 4.0 architecture.",
                3299.00, 50, "https://images.unsplash.com/photo-1583863788434-e58a36330cf0?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Electronics", "JBL Flip 6 Portable Waterproof Bluetooth Speaker",
                "2-way speaker system delivering powerful crystal-clear sound, dual passive radiators, 12 hours playtime.",
                8999.00, 45, "https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=600&q=80"));

        // ──────────────── Fashion (10 products) ────────────────
        list.add(new ProductSeed("Fashion", "Levi's Men's 511 Slim Fit Stretch Denim Jeans",
                "Modern slim-cut jeans with room to move, crafted from premium stretch cotton denim with zip fly.",
                2499.00, 40, "https://images.unsplash.com/photo-1542272604-780c96856592?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "Puma Men's Smashic Casual Sneakers",
                "Classic tennis-inspired street silhouette with SoftFoam+ cushioned footbed and durable rubber outsole.",
                2199.00, 50, "https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "FabIndia Pure Cotton Straight Kurta for Men",
                "Breathable handwoven pure cotton kurta with mandarin collar, full sleeves, and comfortable side slits.",
                1890.00, 35, "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "Biba Women's Printed Anarkali Kurta with Dupatta Set",
                "Elegant traditional floral print cotton Anarkali suit set tailored for festive family gatherings.",
                3499.00, 30, "https://images.unsplash.com/photo-1583391733956-3750e0ff4e8b?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "Ray-Ban Classic Aviator Polarized Sunglasses",
                "Timeless gold metal frame with polarized green classic G-15 crystal lenses for 100% UV protection.",
                7990.00, 25, "https://images.unsplash.com/photo-1511499767150-a48a237f0083?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "Titan Neo Analog Black Dial Men's Watch",
                "Sleek stainless steel strap with mineral glass casing, date display, and 50m water resistance.",
                3995.00, 38, "https://images.unsplash.com/photo-1524805444758-089113d48a6d?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "Wildcraft 35L Water Resistant Laptop Backpack",
                "Multi-compartment ergonomic backpack with padded 15.6-inch laptop sleeve and rain cover included.",
                1799.00, 60, "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "Allen Solly Men's Regular Fit Polo T-Shirt",
                "Soft combed cotton honeycomb polo with tipped ribbed collar, short sleeves, and embroidered chest emblem.",
                899.00, 70, "https://images.unsplash.com/photo-1581655353564-df123a1eb820?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "W for Woman Rayon Flared Ethnic Dress",
                "Flowy contemporary ethnic dress crafted from soft breathable rayon with round neck and three-quarter sleeves.",
                2199.00, 32, "https://images.unsplash.com/photo-1595777457583-95e059d581b8?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Fashion", "Red Tape Men's Classic Leather Formal Oxford Shoes",
                "Handcrafted genuine leather formal shoes with padded footbed and textured anti-skid TPR sole.",
                2099.00, 40, "https://images.unsplash.com/photo-1533867617858-e7b97e060509?auto=format&fit=crop&w=600&q=80"));

        // ──────────────── Home & Kitchen (10 products) ────────────────
        list.add(new ProductSeed("Home & Kitchen", "Prestige Iris 750 Watt Mixer Grinder (3 Jars + 1 Juicer)",
                "Powerful 750W copper motor with stainless steel versatile jars for smooth wet, dry, and chutney grinding.",
                3299.00, 40, "https://images.unsplash.com/photo-1585659722983-3a675dabf23d?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Pigeon by Stovekraft 1.5L Stainless Steel Electric Kettle",
                "Rapid water boiling with automatic cut-off safety, cool-touch handle, and 360-degree swivel cordless base.",
                699.00, 80, "https://images.unsplash.com/photo-1594213114663-d94db9b17125?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Milton Thermosteel Flip Lid Vacuum Insulated Flask (1L)",
                "24-hour temperature retention for hot and cold beverages, food-grade 18/8 stainless steel leak-proof flask.",
                999.00, 65, "https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Hawkins Contura Hard Anodised Inner Lid Pressure Cooker (3L)",
                "Hard anodised body heats fast and uniformly, staying looking new for years; non-toxic and scratch resistant.",
                1825.00, 45, "https://images.unsplash.com/photo-1584990347449-a20d75b3c434?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Wakefit Hollow Fiber Sleeping Bed Pillow (Set of 2)",
                "Medium-firm neck and head support with breathable microfiber casing for restful, cloud-like sleep.",
                799.00, 90, "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Philips Daily Collection 4-Slot Pop-Up Bread Toaster",
                "8 adjustable browning settings with integrated bun warming rack and easy defrost/reheat functions.",
                1999.00, 35, "https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Borosil Glass Mixing Bowls with Lid (Set of 3: 500ml, 900ml, 1.3L)",
                "100% borosilicate microwave, oven, and dishwasher safe glass mixing bowls with tight-fitting air-seal lids.",
                1199.00, 50, "https://images.unsplash.com/photo-1578749556568-bc2c40e68b61?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Solimo 100% Cotton 144 TC King Size Bedsheet with 2 Pillow Covers",
                "Super soft breathable pure cotton double bedsheet with elegant geometric prints and durable stitching.",
                849.00, 60, "https://images.unsplash.com/photo-1616046229478-9901c5536a45?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Kuber Industries Non-Woven Foldable Wardrobe Clothes Organizer",
                "Durable storage boxes with reinforced side handles and clear labels to declutter closets, shirts, and linen.",
                499.00, 110, "https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Home & Kitchen", "Wonderchef Granite Non-Stick Dosa Tawa (28cm)",
                "5-layer healthy granite non-stick coating free from PFOA and heavy metals, gas and induction compatible.",
                1299.00, 40, "https://images.unsplash.com/photo-1590794056226-79ef3a8147e1?auto=format&fit=crop&w=600&q=80"));

        // ──────────────── Beauty & Personal Care (10 products) ────────────────
        list.add(new ProductSeed("Beauty & Personal Care", "Mamaearth Onion Hair Fall Control Shampoo (600ml)",
                "Enriched with red onion seed oil and plant keratin to strengthen roots and noticeably reduce hair fall.",
                499.00, 75, "https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "Minimalist 10% Niacinamide Face Serum (30ml)",
                "Clinically proven daily serum to reduce blemishes, balance sebum production, and soothe redness.",
                599.00, 85, "https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "The Derma Co 1% Hyaluronic Sunscreen Aqua Gel SPF 50 (50g)",
                "Ultra-lightweight broad spectrum UV protection with zero white cast, enriched with hyaluronic acid and vitamin E.",
                449.00, 95, "https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "Philips BT1232/15 Skin-Friendly Cordless Beard Trimmer",
                "Self-sharpening stainless steel blades with USB charging, rounded blade tips, and 30-min continuous cordless run time.",
                899.00, 60, "https://images.unsplash.com/photo-1621607512214-68297480165e?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "Biotique Bio Green Apple Fresh Daily Purifying Shampoo (650ml)",
                "Ayurvedic daily hair wash enriched with natural green apple extracts, sea algae, and centella to restore shine.",
                320.00, 80, "https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "Maybelline New York Colossal Waterproof Mascara (Black)",
                "Delivers dramatic volume with collagen formula and mega brush without clumps, smudge-proof for 12 hours.",
                399.00, 70, "https://images.unsplash.com/photo-1631730486784-5456119f69ae?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "Nivea Men Deep Impact Fresh Deodorant Roll-on (50ml)",
                "Antibacterial black carbon formula providing 48-hour fresh masculine fragrance and powerful odor defense.",
                199.00, 100, "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "Plum Green Tea Pore Cleansing Face Wash (120ml)",
                "Gentle soap-free foaming wash with green tea extract and glycolic acid to combat acne and deep clean pores.",
                315.00, 90, "https://images.unsplash.com/photo-1556228722-d0b5d95d10d8?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "Forest Essentials Delicate Facial Cleanser Kashmiri Saffron (200ml)",
                "Purifying Ayurvedic cleanser with steam distilled Kashmiri saffron, neem leaf, and organic kewda water.",
                1475.00, 35, "https://images.unsplash.com/photo-1608248597359-59750534c090?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Beauty & Personal Care", "L'Oreal Paris Total Repair 5 Deep Restoring Hair Mask (200ml)",
                "Concentrated ceramide conditioning mask battling 5 signs of damage: hair fall, dryness, roughness, dullness, split ends.",
                399.00, 65, "https://images.unsplash.com/photo-1571781926291-c477ebfd024b?auto=format&fit=crop&w=600&q=80"));

        // ──────────────── Books & Stationery (10 products) ────────────────
        list.add(new ProductSeed("Books & Stationery", "Atomic Habits by James Clear (Paperback)",
                "An easy and proven framework for improving every day through tiny behavioral changes and identity formation.",
                499.00, 80, "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "The Psychology of Money by Morgan Housel",
                "Timeless lessons on wealth, greed, and happiness told through 19 captivating short stories on financial decisions.",
                340.00, 90, "https://images.unsplash.com/photo-1592496431122-2349e0fbc666?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "Classmate Pulse Spiral Bound Notebook (Pack of 3, 300 Pages Each)",
                "Single line ruling, ozone-treated super-bright paper with sturdy poly cover and micro-perforated sheets.",
                399.00, 100, "https://images.unsplash.com/photo-1531346878377-a5be20888e57?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "Parker Vector Standard Blue Ink Rollerball Pen",
                "Refillable fine stainless steel nib rollerball pen featuring the iconic Parker arrow clip and smooth ink flow.",
                310.00, 70, "https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "Casio FX-991CW ClassWiz Non-Programmable Scientific Calculator",
                "High-definition 4-gradation natural textbook display with 540+ functions for engineering and mathematics students.",
                1595.00, 40, "https://images.unsplash.com/photo-1574607383476-f517f260d30b?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "Ikigai: The Japanese Secret to a Long and Happy Life",
                "Inspiring international bestseller on discovering purposeful everyday longevity and living with joy.",
                380.00, 85, "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "Faber-Castell 24 Tri-Grip Water Soluble Color Pencils",
                "Vibrant pigmentation with smooth triangular ergonomic grip and free artist paint brush included in pack.",
                240.00, 75, "https://images.unsplash.com/photo-1513542789411-b6a5d4f31634?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "Kangaro Heavy Duty Paper Punch (DP-600, 2-Hole)",
                "All-metal construction punching up to 32 sheets with centering guide bar and removable waste tray.",
                450.00, 50, "https://images.unsplash.com/photo-1586075010923-2dd4570fb338?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "Rich Dad Poor Dad by Robert T. Kiyosaki",
                "Personal finance classic that explodes the myth that high income equals wealth and teaches real asset building.",
                399.00, 65, "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&w=600&q=80"));
        list.add(new ProductSeed("Books & Stationery", "Solo Presentation Display Book Folder (40 Clear Pockets, A4)",
                "Anti-static clear pocket portfolio folder with spine label insert for certificates and presentation documents.",
                280.00, 80, "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=600&q=80"));

        return list;
    }
}
