package com.specskart.whatsapp;

public enum BotIntent {
    // Frames flow: kept working end-to-end, just not linked from the welcome message while
    // the client's lens-only funnel is the one shown (see WhatsAppBotService.sendWelcome).
    FIND_FRAMES, EXPLORE_FRAMES, VISIT_WEBSITE,
    RESULTS_SHOW_FRAMES, RESULTS_NOT_NOW,
    HELP_CHOOSE, BUDGET_LOW, BUDGET_MED, BUDGET_HIGH,
    EXPLORE_LENS,
    TRACK_ORDER,
    MENU,
    CARE,
    FAQ,
    // The FB ad's prefilled "can I have more information" — lens link, then the menu.
    MORE_INFO,
    // "how much are your frames?" and any other frames question: the client's answer is the
    // price range + come to the shop, while online frame orders are still being built.
    PRICE, FRAMES,
    // "Yes" to "would you like to know what suits you?" — the face-shape chart
    SUITS_YOU,
    // Chat -> shop visit (2026-10-20): book the free eye test; frame photos in the chat
    BOOK_VISIT, FRAME_PHOTOS,
    // Spin & Win (2026-10-21): one prize spin per customer
    SPIN,
    // "do you sell cleaning solution / pouches?" (2026-10-23): what's in stock + come to the shop
    ACCESSORIES,
    GREETING, UNKNOWN
}
