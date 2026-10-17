package io.github.serfom256.intentflow.examples.shop.domain;

public final class Descriptions {

    public static final String PRODUCTS = "Items sold in the shop";
    public static final String PRODUCT_NAME = "model name including the product line, e.g. Galaxy A56, iPhone 16 Pro, "
            + "MacBook Air 13 M3; match it with ~";
    public static final String PRODUCT_BRAND = "manufacturer only, e.g. Apple, Samsung; product lines such as Galaxy, "
            + "iPhone or ThinkPad are part of name";
    public static final String PRODUCT_CATEGORY = "kind of product; words such as laptops, phones, tablets or "
            + "headphones mean this field";
    public static final String PRODUCT_PRICE = "retail price in USD";
    public static final String PRODUCT_DISCOUNT = "discount in percent of price; null when the product is not discounted";
    public static final String PRODUCT_RATING = "average customer rating from 0 to 5";
    public static final String PRODUCT_STOCK = "units in the warehouse";
    public static final String PRODUCT_ACTIVE = "false when the product is no longer sold";
    public static final String PRODUCT_CREATED_AT = "when the product was added to the catalog";

    public static final String CUSTOMERS = "Registered shop customers";
    public static final String CUSTOMER_CITY = "city name in English, e.g. Berlin, Tokyo, New York";
    public static final String CUSTOMER_COUNTRY = "ISO 3166 two-letter code, e.g. US, DE, FR";
    public static final String CUSTOMER_VIP = "member of the loyalty program";
    public static final String CUSTOMER_CREATED_AT = "when the customer signed up";

    public static final String ORDERS = "Customer orders";
    public static final String ORDER_CUSTOMER_ID = "id of the customer who placed the order";
    public static final String ORDER_TOTAL = "order total in USD";
    public static final String ORDER_ITEMS_COUNT = "number of items in the order";
    public static final String ORDER_CREATED_AT = "when the order was placed";

    private Descriptions() {
    }
}
